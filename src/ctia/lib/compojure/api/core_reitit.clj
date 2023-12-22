(ns ctia.lib.compojure.api.core-reitit
  "Exposes the API of compojure.api.core v1.1.13
  
  Always use this namespace over compojure.api.{core,sweet}
  as it also loads the CTIA routing extensions."
  (:require [compojure.api.common :as common]
            [clojure.set :as set]
            [ctia.http.middleware.auth :as mid]
            [ctia.auth :as auth]
            [schema-tools.core :as st]
            [ctia.lib.compojure.api.core :refer [check-return-banned!]]))

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
        _ (check-return-banned! options)
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

(def ^:private allowed-endpoint-options #{:responses :capabilities :auth-identity :identity-map :query-params :path-params})

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

(defn ^:private parse-params [params]
  (assert (vector? params) (str "params must be a vector: " (pr-str params)))
  (loop [params params
         result (sorted-map)]
    (if (empty? params)
      result
      (let [f (first params)]
        (if (map? f)
          ;; [{wait_for :- (describe s/Bool "wait for patched entity to be available for search") nil}]
          ;; =>
          ;; {wait_for {:schema (describe s/Bool "wait for patched entity to be available for search")
          ;;            :default nil}}
          (let [m f
                _ (assert (= 2 (count m)) (str "incorrect map params syntax, must be length 2: " (pr-str m)))
                [[sym _] [schema default]]
                (let [[l r] (seq m)]
                  (if (-> l val (= :-))
                    [l r]
                    [r l]))]
            (recur (next params)
                   (assoc result sym {:schema schema
                                      :default default})))
          ;; [wait_for :- (describe s/Bool "wait for patched entity to be available for search")]
          ;; =>
          ;; {wait_for {:schema (describe s/Bool "wait for patched entity to be available for search")}}
          (let [[turnstile schema & params] (next params)
                sym f]
            (assert (simple-symbol? sym) (str "expected first value to be a simple symbol in " [sym turnstile schema]))
            (assert (= :- turnstile) (str "expected :- after " sym))
            (assert schema (str "missing schema in params for: " sym))
            (recur params
                   (assoc result sym {:schema schema}))))))))

(defn ^:private restructure-endpoint [http-kw path arg & args]
  (assert (simple-keyword? http-kw))
  (assert (or (= [] arg)
              (simple-symbol? arg))
          (pr-str arg))
  (let [[{:keys [capabilities auth-identity identity-map] :as options} body] (common/extract-parameters args true)
        _ (check-return-banned! options)
        _ (when-some [extra-keys (not-empty (set/difference (set (keys options))
                                                            allowed-endpoint-options))]
            (throw (ex-info (str "Not allowed these options in endpoints: "
                                 (pr-str (sort extra-keys)))
                            {})))
        responses (when-some [[_ responses] (find options :responses)]
                    `(compojure->reitit-responses ~responses))
        query-params (when-some [[_ query-params] (find options :query-params)]
                       (parse-params query-params))
        path-params (when-some [[_ path-params] (find options :path-params)]
                      (parse-params path-params))
        greq (*gensym* "req")
        gparameters (delay (*gensym* "parameters"))
        gidentity (delay (*gensym* "identity"))
        ;; `gs` are uncapturable variables via gensym. they are bound first so
        ;; they can be bound to capturable expressions.
        ;; `scoped` are capturable variables provided by user. they are bound last,
        ;; and they are bound to uncapturable expressions.
        {:keys [scoped gs]
         :or {gs [] scoped []}} (merge-with
                                  into
                                  (when (simple-symbol? arg)
                                    {:scoped [arg greq]})
                                  (when (or query-params
                                            path-params
                                            ;; TODO
                                            )
                                    {:gs [@gparameters (list :parameters greq)]})
                                  (when query-params
                                    (let [gquery (*gensym* "query")]
                                      (apply merge-with into 
                                             {:gs [gquery (list :query @gparameters)]}
                                             (map (fn [[sym {:keys [default]}]]
                                                    (let [gdefault (*gensym* (str (name sym) "-default"))]
                                                      {:gs [gdefault default]
                                                       :scoped [sym (list `get gquery (keyword sym) gdefault)]}))
                                                  query-params))))
                                  (when path-params
                                    (let [gpath (*gensym* "path")]
                                      (apply merge-with into 
                                             {:gs [gpath (list :path @gparameters)]}
                                             (map (fn [[sym {:keys [schema] :as opts}]]
                                                    (assert (= [:schema] (keys opts)) "no default allowed for path params")
                                                    {:scoped [sym (list `get gpath (keyword sym))]})
                                                  path-params))))
                                  (when (or auth-identity identity-map)
                                    {:gs [@gidentity (list :identity greq)]})
                                  (when auth-identity
                                    (assert (simple-symbol? auth-identity) (str ":auth-identity must be a simple symbol: "
                                                                                (pr-str auth-identity)))
                                    {:scoped [auth-identity @gidentity]})
                                  (when identity-map
                                    (assert (simple-symbol? identity-map) (str ":identity-map must be a simple symbol: "
                                                                               (pr-str identity-map)))
                                    {:scoped [identity-map (list `auth/ident->map @gidentity)]}))
        _ (when (seq gs)
            (assert (apply distinct? (map first (partition 2 gs)))))
        _ (when (seq scoped)
            (let [names (map first (partition 2 scoped))]
              ;; we can lift this once we ensure we parse options deterministically. i.e., that `options` is
              ;; in the same order as provided by the user.
              (assert (apply distinct? names)
                      (str "ERROR: cannot shadow variables in endpoints, please rename to avoid clashes: "
                           (pr-str (sort names))))))]
    [path {http-kw (cond-> {:handler `(fn [~greq]
                                        (let ~(into gs scoped)
                                          (do ~@body)))}
                     responses (assoc :responses responses)
                     capabilities (update :middleware (fnil conj [])
                                          [`(mid/wrap-capabilities ~capabilities)])
                     query-params (assoc-in [:parameters :query]
                                            (list `st/optional-keys
                                                  (into {} (map (fn [[sym {:keys [schema]}]]
                                                                  {(keyword sym) schema}))
                                                        query-params)))
                     path-params (assoc-in [:parameters :path]
                                           (into {} (map (fn [[sym {:keys [schema]}]]
                                                           {(keyword sym) schema}))
                                                 path-params)))}]))

(defmacro GET     {:style/indent 2} [& args] (apply restructure-endpoint :get args))
(defmacro ANY     {:style/indent 2} [& args] (apply restructure-endpoint :any args))
(defmacro PATCH   {:style/indent 2} [& args] (apply restructure-endpoint :patch args))
(defmacro DELETE  {:style/indent 2} [& args] (apply restructure-endpoint :delete args))
(defmacro POST    {:style/indent 2} [& args] (apply restructure-endpoint :post args))
(defmacro PUT     {:style/indent 2} [& args] (apply restructure-endpoint :put args))
