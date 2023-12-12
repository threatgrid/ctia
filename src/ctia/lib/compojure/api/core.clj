(ns ctia.lib.compojure.api.core
  "Exposes the API of compojure.api.core v1.1.13
  
  Always use this namespace over compojure.api.{core,sweet}
  as it also loads the CTIA routing extensions."
  (:require [compojure.api.common :as common]
            [clojure.set :as set]
            [ctia.http.middleware.auth :as magic]))

;; banned
(require '[compojure.api.core :as core])

(assert magic/add-id-to-request
        (str "Never delete the :require of ctia.http.middleware.auth! "
             "It has magic defmethod calls that are crucial to the "
             "reliability of CTIA's startup. "
             "See also: https://github.com/threatgrid/iroh/issues/4458"))

(defn routes
  "Create a Ring handler by combining several handlers into one."
  [& handlers]
  (apply core/routes handlers))

(defmacro defroutes
  "Define a Ring handler function from a sequence of routes.
  The name may optionally be followed by a doc-string and meadata map."
  {:style/indent 1}
  [name & routes]
  `(core/defroutes ~name ~@routes))

(defmacro let-routes
  "Takes a vector of bindings and a body of routes.
  Equivalent to: `(let [...] (routes ...))`"
  {:style/indent 1}
  [bindings & body]
  `(core/let-routes ~bindings ~@body))

(defn undocumented
  "Routes without route-documentation. Can be used to wrap routes,
  not satisfying compojure.api.routes/Routing -protocol."
  [& handlers]
  (apply core/undocumented handlers))

(defmacro middleware
  "Wraps routes with given middlewares using thread-first macro.
  Note that middlewares will be executed even if routes in body
  do not match the request uri. Be careful with middlewares that
  have side-effects."
  {:style/indent 1}
  [middleware & body]
  `(core/middleware ~middleware ~@body))

(def ^:private allowed-context-options #{:tags :capabilities :description :return :summary})
(def ^:private unevalated-options #{:tags})

(def ^:private ^:dynamic *gensym* gensym)

(defmacro context
  "Like compojure.api.core/context, except the binding vector must be empty and
  no binding-style options are allowed. This is to prevent the passed routes
  from being reinitialized on every request."
  {:style/indent 2}
  [path arg & args]
  (assert (vector? arg))
  (assert (= [] arg) (str "Not allowed to bind anything in context, push into HTTP verbs instead: " (pr-str arg)))
  (let [[options body] (common/extract-parameters args true)
        _ (when-some [extra-keys (not-empty (set/difference (set (keys options))
                                                            allowed-context-options))]
            (throw (ex-info (str "Not allowed these options in `context`, push into HTTP verbs instead: "
                                 (pr-str (sort extra-keys)))
                            {})))
        groutes (*gensym* "routes")
        option->g (into {} (comp (remove unevalated-options)
                                 (map (juxt identity (comp *gensym* name))))
                        (keys options))]
    `(let [~groutes (core/routes ~@body)
           ~@(mapcat (fn [[k v]]
                       (when-some [g (option->g k)]
                         [g v]))
                     options)]
       (core/context ~path ~arg
                     ~@(mapcat (fn [[k v]]
                                 [k (option->g k v)])
                               options)
                     ~groutes))))

(defn ^:private restructure-endpoint [compojure-macro path arg args]
  (let [[options body] (common/extract-parameters args true)]
    (if (= [] arg)
      ;; can savely let-bind values from its middleware to outside this endpoint
      ;; since it doesn't bind any variables (e.g., req)
      (let [{:keys [lets options]} (reduce-kv (fn [acc k v]
                                                (let [g (*gensym* (name k))
                                                      [lets v] (case k
                                                                 ;; (ANY "*" [] :return SCHEMA ...)
                                                                 ;; =>
                                                                 ;; (let [return__0 SCHEMA] (ANY "*" [] :return return__0 ...)
                                                                 (:capabilities :return :description :summary) [[g v] g]
                                                                 ;; (ANY "*" [] :body [sym SCHEMA ...] ...)
                                                                 ;; =>
                                                                 ;; (let [body__0 SCHEMA] (ANY "*" [] :body [sym body__0 ...] ...)
                                                                 :body (let [_ (assert (vector? v))
                                                                             _ (assert (<= 2 (count v) 3))
                                                                             [b s m] v
                                                                             _ (assert (simple-symbol? b))
                                                                             _ (when (= 3 (count v))
                                                                                 (assert (map? m)))]
                                                                         [[g s] (assoc v 1 g)])
                                                                 :tags [[] v])]
                                                  (-> acc
                                                      (update :lets into lets)
                                                      (assoc-in [:options k] v))))
                                              {:lets []
                                               :options {}}
                                              options)]
        (cond->> `(~compojure-macro ~path ~arg
                                    ~@(mapcat identity options)
                                    ~@body)
          (seq lets) (list `let lets)))
      ;; the best we can do is just assert that expressions are symbols. that will
      ;; force the user to let-bind them.
      (do (doseq [[k v] options]
            (case k
              :body (let [[_ s :as body] v]
                      (assert (vector? body))
                      (assert (<= 2 (count body) 3))
                      (when-not (symbol? s)
                        (throw (ex-info (str "Please let-bind the :body schema like so: "
                                             (pr-str (list 'let ['s# s] (list (symbol (name compojure-macro)) path arg :body (assoc body 1 's#) '...))))
                                        {}))))
              (:return :capabilities) (when-not (symbol? v)
                                        (throw (ex-info (str (format "Please let-bind %s like so: " k)
                                                             (pr-str (list 'let ['v# v] (list (symbol (name compojure-macro)) path arg k 's# '...))))
                                                        {})))
              ;; I think these only exist at initialization time, even though they are expressions. but with all the compojure-api inference that
              ;; reevaluates routes twice, it might be wise to require them to be let-bound?
              (:description :summary) nil
              ;; values
              :tags nil))
          (list* compojure-macro path arg args)))))

(defmacro GET     {:style/indent 2} [path arg & args] (restructure-endpoint `core/GET     path arg args))
(defmacro ANY     {:style/indent 2} [path arg & args] (restructure-endpoint `core/ANY     path arg args))
(defmacro HEAD    {:style/indent 2} [path arg & args] (restructure-endpoint `core/HEAD    path arg args))
(defmacro PATCH   {:style/indent 2} [path arg & args] (restructure-endpoint `core/PATCH   path arg args))
(defmacro DELETE  {:style/indent 2} [path arg & args] (restructure-endpoint `core/DELETE  path arg args))
(defmacro OPTIONS {:style/indent 2} [path arg & args] (restructure-endpoint `core/OPTIONS path arg args))
(defmacro POST    {:style/indent 2} [path arg & args] (restructure-endpoint `core/POST    path arg args))
(defmacro PUT     {:style/indent 2} [path arg & args] (restructure-endpoint `core/PUT     path arg args))
