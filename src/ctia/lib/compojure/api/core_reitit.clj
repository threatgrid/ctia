(ns ctia.lib.compojure.api.core-reitit
  "Exposes the API of compojure.api.core v1.1.13
  
  Always use this namespace over compojure.api.{core,sweet}
  as it also loads the CTIA routing extensions."
  (:require [compojure.api.common :as common]
            [clojure.set :as set]
            [ctia.http.middleware.auth :as mid]))

;;TODO this isn't right
(defn routes
  "Create a Ring handler by combining several handlers into one."
  [& handlers]
  (vec handlers))

(defn undocumented
  "Routes without route-documentation. Can be used to wrap routes,
  not satisfying compojure.api.routes/Routing -protocol."
  [& handlers]
  (assert nil)
  #_
  (apply core/undocumented handlers))

(defmacro middleware
  "Wraps routes with given middlewares using thread-first macro.
  Note that middlewares will be executed even if routes in body
  do not match the request uri. Be careful with middlewares that
  have side-effects."
  {:style/indent 1}
  [middleware & body]
  `["" {:middleware ~middleware}
    [~@body]])

(def ^:private allowed-context-options #{:tags :capabilities :description :responses :summary})

(def ^:private ^:dynamic *gensym* gensym)

(defmacro context
  "Like compojure.api.core/context, except the binding vector must be empty and
  no binding-style options are allowed. This is to prevent the passed routes
  from being reinitialized on every request."
  {:style/indent 2}
  [path arg & args]
  (assert (vector? arg))
  (assert (= [] arg) (str "Not allowed to bind anything in context, push into HTTP verbs instead: " (pr-str arg)))
  (let [[options body] ((requiring-resolve 'compojure.api.common/extract-parameters) args true)
        _ (when-some [extra-keys (not-empty (set/difference (set (keys options))
                                                            allowed-context-options))]
            (throw (ex-info (str "Not allowed these options in `context`, push into HTTP verbs instead: "
                                 (pr-str (sort extra-keys)))
                            {})))
        reitit-opts (let [{:keys [tags description summary capabilities responses]} options]
                      (cond-> {}
                        tags (assoc-in [:swagger :tags] (list 'quote tags))
                        description (assoc-in [:swagger :description] description)
                        summary (assoc-in [:swagger :summary] summary)
                        capabilities (update :middleware (fnil conj [])
                                             [`(mid/wrap-capabilities ~capabilities)])
                        responses (assoc :responses `(compojure->reitit-responses ~responses))))]
    `[~path
      ~@(some-> (not-empty reitit-opts) list)
      (routes ~@body)]))

(def ^:private allowed-endpoint-options #{:responses :capabilities})

(defn validate-responses! [responses]
  (assert (map? responses))
  (doseq [[k v] responses]
    (assert (nat-int? k) (pr-str k))
    (assert (<= 0 k 599) (pr-str k))
    (when (some? v)
      (assert (map? v) (pr-str v))
      (assert (:schema v) (pr-str v))
      (assert (-> v keys set (disj :schema :description) empty?) (pr-str v)))))

;; {200 {:schema Foo}} => {200 {:body Foo}}
(defn compojure->reitit-responses [responses]
  (validate-responses! responses)
  (update-vals responses (fn [rhs]
                           (when (some? rhs)
                             (assert (map? rhs))
                             (let [unknown-keys (-> rhs keys set (disj :schema))]
                               (assert (empty? unknown-keys) unknown-keys))
                             (assert (:schema rhs))
                             (set/rename-keys rhs {:schema :body})))))

(defn ^:private restructure-endpoint [http-kw path arg & args]
  (assert (simple-keyword? http-kw))
  (assert (or (= [] arg)
              (simple-symbol? arg))
          (pr-str arg))
  (let [[{:keys [responses capabilities] :as options} body] ((requiring-resolve 'compojure.api.common/extract-parameters) args true)
        _ (when-some [extra-keys (not-empty (set/difference (set (keys options))
                                                            allowed-endpoint-options))]
            (throw (ex-info (str "Not allowed these options in endpoints: "
                                 (pr-str (sort extra-keys)))
                            {})))
        responses (when responses
                    `(compojure->reitit-responses ~responses))
        greq (*gensym* "req")]
    [path {http-kw (cond-> {:handler `(fn [~greq]
                                        (let [~@(when (simple-symbol? arg)
                                                  [arg greq])]
                                          (do ~@body)))}
                     responses (assoc :responses responses)
                     capabilities (update :middleware (fnil conj [])
                                          [`(mid/wrap-capabilities ~capabilities)]))}]))

(defmacro GET     {:style/indent 2} [& args] (apply restructure-endpoint :get args))
(defmacro ANY     {:style/indent 2} [& args] (apply restructure-endpoint :any args))
(defmacro PATCH   {:style/indent 2} [& args] (apply restructure-endpoint :patch args))
(defmacro DELETE  {:style/indent 2} [& args] (apply restructure-endpoint :delete args))
(defmacro POST    {:style/indent 2} [& args] (apply restructure-endpoint :post args))
(defmacro PUT     {:style/indent 2} [& args] (apply restructure-endpoint :put args))
