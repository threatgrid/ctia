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

(defn ^:private restructure-endpoint
  "Ensures endpoint options like :body, :return etc only initialize once
  by either let-binding expressions, or throwing exceptions if reevaluation is possible
  but automatic let-binding is not safe."
  [compojure-macro path arg args]
  (let [[options body] (common/extract-parameters args true)]
    (if (= [] arg)
      ;; can safely let-bind values from its middleware to outside this endpoint
      ;; since it doesn't bind any variables (e.g., req)
      (let [{:keys [lets options]} (reduce-kv (fn [acc k v]
                                                (let [g (*gensym* (name k))
                                                      [lets v] (case k
                                                                 ;; (ANY "*" [] :return SCHEMA ...)
                                                                 ;; =>
                                                                 ;; (let [return__0 SCHEMA] (core/ANY "*" [] :return return__0 ...))
                                                                 (:capabilities :return) [[g v] g]
                                                                 ;; (ANY "*" [] :body [sym SCHEMA ...] ...)
                                                                 ;; =>
                                                                 ;; (let [body__0 SCHEMA] (core/ANY "*" [] :body [sym body__0 ...] ...))
                                                                 (:body :query) (do (assert (vector? v))
                                                                                    (assert (<= 2 (count v) 3))
                                                                                    [[g (nth v 1)] (assoc v 1 g)])
                                                                 ;; (ANY "/:left/:right" [] :path-params [left :- SCHEMA0, right :- SCHEMA1] ...)
                                                                 ;; =>
                                                                 ;; (let [left__0 SCHEMA0, right__1 SCHEMA1]
                                                                 ;;   (core/ANY "/:left/:right" []
                                                                 ;;     :path-params [left :- left__0, right :- right__0]
                                                                 ;;      ...))
                                                                 :path-params (let [_ (assert (vector? v))
                                                                                    _ (assert (zero? (mod (count v) 3)))
                                                                                    groups (partition-all 3 v)]
                                                                                (reduce (fn [[lets v] [b |- s]]
                                                                                          {:pre [(= :- |-)
                                                                                                 (simple-symbol? b)]}
                                                                                          (let [g (*gensym* b)]
                                                                                            [(conj lets g s) (conj v b |- g)]))
                                                                                        [[] []]
                                                                                        (partition-all 3 v)))
                                                                 ;; (ANY "*" [] :tags #{:foo} ...)
                                                                 ;; =>
                                                                 ;; (core/ANY "*" [] :tags #{:foo} ...)
                                                                 (:tags :auth-identity :identity-map :description :summary :no-doc :produces) [[] v]
                                                                 ;;FIXME
                                                                 (:query-params :responses :middleware) [[] v])]
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
              ;; fail if schema is not a local/var dereference and show user how to let-bind it
              (:query :body) (let [[_ s :as body] v]
                               (assert (vector? body))
                               (assert (<= 2 (count body) 3))
                               (when-not (symbol? s)
                                 (throw (ex-info (str (format "Please let-bind the %s schema like so: " k)
                                                      (pr-str (list 'let ['s# s] (list (symbol (name compojure-macro)) path arg k (assoc body 1 's#) '...))))
                                                 {}))))
              ;; fail if right-hand-side is not a local/var dereference and show user how to let-bind it
              (:return :capabilities :no-doc :produces) (when-not (or (symbol? v)
                                                                      (and (= :no-doc k)
                                                                           (boolean? v)))
                                                          (throw (ex-info (str (format "Please let-bind %s like so: " k)
                                                                               (pr-str (list 'let ['v# v] (list (symbol (name compojure-macro)) path arg k 's# '...))))
                                                                          {})))
              ;; fail if any schemas are not symbols
              :path-params (let [_ (assert (vector? v))
                                 _ (assert (zero? (mod (count v) 3)))]
                             (doseq [[path |- s] (partition-all 3 v)]
                               (when-not (symbol? s)
                                 (throw (ex-info (str (format "Please let-bind %s in %s like so: " path k)
                                                      (pr-str (list 'let ['s# s] (list (symbol (name compojure-macro)) path arg k [path |- 's#] '...))))
                                                 {})))))
              ;; swagger only
              (:description :summary) nil
              ;; values
              :tags nil
              ;; binders
              (:auth-identity :identity-map) nil
              ;;FIXME
              (:query-params :responses :middleware) nil))
          (list* compojure-macro path arg args)))))

(defmacro GET     {:style/indent 2} [path arg & args] (restructure-endpoint `core/GET     path arg args))
(defmacro ANY     {:style/indent 2} [path arg & args] (restructure-endpoint `core/ANY     path arg args))
(defmacro HEAD    {:style/indent 2} [path arg & args] (restructure-endpoint `core/HEAD    path arg args))
(defmacro PATCH   {:style/indent 2} [path arg & args] (restructure-endpoint `core/PATCH   path arg args))
(defmacro DELETE  {:style/indent 2} [path arg & args] (restructure-endpoint `core/DELETE  path arg args))
(defmacro OPTIONS {:style/indent 2} [path arg & args] (restructure-endpoint `core/OPTIONS path arg args))
(defmacro POST    {:style/indent 2} [path arg & args] (restructure-endpoint `core/POST    path arg args))
(defmacro PUT     {:style/indent 2} [path arg & args] (restructure-endpoint `core/PUT     path arg args))
