;; TODO should :query-params be automatically optionalized? :query?
(ns ctia.lib.compojure.api.core-reitit
  "Exposes the API of compojure.api.core v1.1.13
  
  Always use this namespace over compojure.api.{core,sweet}
  as it also loads the CTIA routing extensions."
  (:require ;;TODO remove dependency
            [compojure.api.common :as common]
            [ctia.lib.compojure.api.core-common :refer [check-return-banned!]]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [ctia.http.middleware.auth :as mid]
            [ctia.auth :as auth]
            [schema-tools.core :as st]))

;;TODO this isn't right
(defn routes
  "Create a Ring handler by combining several handlers into one."
  [& handlers]
  (vec handlers))

(defmacro undocumented
  "Routes without route-documentation. Can be used to wrap routes,
  not satisfying compojure.api.routes/Routing -protocol."
  [& handlers]
  (assert nil "TODO undocumented")
  #_
  `(apply core/undocumented handlers))

(defmacro middleware
  "Wraps routes with given middlewares using thread-first macro.
  Note that middlewares will be executed even if routes in body
  do not match the request uri. Be careful with middlewares that
  have side-effects."
  {:style/indent 1}
  [middleware & body-exprs]
  `["" {:middleware ~middleware}
    [~@body-exprs]])

(def ^:private allowed-context-options #{:tags :capabilities :description :responses :summary})

(def ^:private ^:dynamic *gensym* gensym)

(defmacro context
  "Like compojure.api.core/context, except the binding vector must be empty and
  no binding-style options are allowed. This is to prevent the passed routes
  from being reinitialized on every request."
  {:style/indent 2}
  [path arg & args]
  (when-not (and (vector? arg)
                 (= [] arg))
    (throw (ex-info (str "Not allowed to bind anything in context, push into HTTP verbs instead: " (pr-str arg))
                    {})))
  (let [[options body-exprs] (common/extract-parameters args true)
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
                        capabilities (update :middleware (fn [prev]
                                                           (assert (not prev))
                                                           [[`(mid/wrap-capabilities ~capabilities)]]))
                        responses (assoc :responses `(compojure->reitit-responses ~responses))))]
    `[~path
      ~@(some-> (not-empty reitit-opts) list)
      (routes ~@body-exprs)]))

(def ^:private allowed-endpoint-options #{:responses :capabilities :auth-identity :identity-map :query-params :path-params
                                          :description :tags :no-doc :summary :produces :middleware :query :body})
(comment
  ;; todo list
  (set/difference @#'ctia.lib.compojure.api.core/allowed-endpoint-options
                  allowed-endpoint-options)
  
  )

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

;; idea: we could support a lot more `context` restructure middleware if we asserted that
;; bindings created from a `context` could only be used in route bodies.
;; OK
;;   (context "" req
;;     (GET "" [] req))
;;   (context "" []
;;     :body [body Foo]
;;     (GET "" [] foo))
;; banned
;;   (context "" req
;;     :middleware [req]
;;     (GET "" [] req))
;;   (context "" req
;;     :body [req req]
;;     (GET "" [] req))
(defn ^:private prevent-scoping-difference-error!
  [arg options]
  (walk/postwalk (fn [s]
                   (if (= arg s)
                     (throw (ex-info (format (str "There is a key difference in scoping between compojure-api and "
                                                  "our compilation to reitit. The request has been bound to %s "
                                                  "but this symbol occurs in the restructuring options. "
                                                  "The request is not in scope here in reitit, so "
                                                  "please rename %s so this incomplete analysis can rule out this "
                                                  "mistake.")
                                             arg arg)
                                     {}))
                     s))
                 options)
  nil)

(defn ^:private restructure-endpoint [http-kw path arg & args]
  (assert (simple-keyword? http-kw))
  (assert (or (= [] arg)
              (simple-symbol? arg))
          (pr-str arg))
  (let [[{:keys [capabilities auth-identity identity-map tags middleware] :as options} body-exprs] (common/extract-parameters args true)
        _ (check-return-banned! options)
        _ (when (simple-symbol? arg)
            (prevent-scoping-difference-error! arg options))
        _ (when-some [extra-keys (not-empty (set/difference (set (keys options))
                                                            allowed-endpoint-options))]
            (throw (ex-info (str "Not allowed these options in endpoints: "
                                 (pr-str (sort extra-keys)))
                            {})))
        responses (when-some [[_ responses] (find options :responses)]
                    `(compojure->reitit-responses ~responses))
        query-params (when-some [[_ query-params] (find options :query-params)]
                       (parse-params query-params))
        query (when-some [[_ [bind schema :as query]] (find options :query)]
                (when query-params
                  (throw (ex-info "Cannot use both :query-params and :query, please combine them."
                                  {})))
                (when-not (and (vector? query) (= 2 (count query)))
                  (throw (ex-info ":query must be a vector of length 2" {})))
                (assert bind)
                (assert schema)
                {:bind bind
                 :schema schema})
        body (when-some [[_ [bind schema :as body]] (find options :body)]
               (when-not (and (vector? body) (= 2 (count body)))
                 (throw (ex-info (str ":body must be a vector of length 2: " (pr-str body)) {})))
               (assert bind)
               (assert schema)
               {:bind bind
                :schema schema})
        path-params (when-some [[_ path-params] (find options :path-params)]
                      (parse-params path-params))
        greq (*gensym* "req")
        gparameters (delay (*gensym* "parameters"))
        gidentity (delay (*gensym* "identity"))
        needs-parameters? (or query-params path-params query body)
        ;; `gs` are uncapturable variables via gensym. they are bound first so
        ;; they can be bound to capturable expressions.
        ;; `scoped` are capturable variables provided by user. they are bound last,
        ;; and they are bound to uncapturable expressions.
        {:keys [scoped gs]
         :or {gs [] scoped []}} (merge-with
                                  into
                                  (when (simple-symbol? arg)
                                    {:scoped [arg greq]})
                                  (when needs-parameters?
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
                                  (when-some [{:keys [bind]} query]
                                    (let [gquery (*gensym* "query")]
                                      {:gs [gquery (list :query @gparameters)]
                                       :scoped [bind gquery]}))
                                  (when-some [{:keys [bind]} body]
                                    (let [gbody (*gensym* "body")]
                                      {:gs [gbody (list :body @gparameters)]
                                       :scoped [bind gbody]}))
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
                                          (do ~@body-exprs)))}
                     (contains? options :description) (assoc-in [:swagger :description] (:description options))
                     ;; literal in compojure-api, so we conserve the semantics
                     tags (assoc-in [:swagger :tags] (list 'quote tags))
                     (contains? options :no-doc) (assoc-in [:swagger :no-doc] (:no-doc options))
                     (contains? options :summary) (assoc-in [:swagger :summary] (:summary options))
                     (contains? options :produces) (assoc-in [:swagger :produces] (:produces options))
                     responses (assoc :responses responses)
                     ;; made :middleware an expression, not sure what compojure-api does here.
                     middleware (update :middleware (fn [prev]
                                                      (assert (not prev))
                                                      middleware))
                     capabilities (update :middleware (fn [prev]
                                                        (let [this-middleware [`(mid/wrap-capabilities ~capabilities)]]
                                                          ;; how to combine with existing :middleware? just ask the user do it.
                                                          (when prev
                                                            (throw (ex-info (format
                                                                              (str "Combining :middleware and :capabilities not yet supported. "
                                                                                   "Please use :middleware %s instead of :capabilities %s.\n"
                                                                                   "The complete middleware might look like: :middleware (conj %s %s).")
                                                                              (pr-str this-middleware) (pr-str capabilities)
                                                                              (pr-str prev) (pr-str (first this-middleware)))
                                                                            {})))
                                                          [this-middleware])))
                     query-params (assoc-in [:parameters :query]
                                            ;; TODO does compojure-api optionalize?
                                            (list `st/optional-keys
                                                  (into {} (map (fn [[sym {:keys [schema]}]]
                                                                  {(keyword sym) schema}))
                                                        query-params)))
                     query (update-in [:parameters :query] (fn [prev]
                                                             (assert (not prev))
                                                             ;; TODO does compojure-api optionalize?
                                                             (:schema query)))
                     body (update-in [:parameters :body] (fn [prev]
                                                           (assert (not prev))
                                                           (:schema body)))
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
