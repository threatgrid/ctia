(ns ctia.lib.compojure.api.core-reitit-test
  (:require [ctia.lib.compojure.api.core-reitit :as sut]
            [ctia.http.middleware.auth :as mid]
            [reitit.ring :as ring]
            [clojure.test :refer [deftest is testing]]
            [ctia.lib.compojure.api.core-compojure-test :refer [is-banned-macro]]
            [ring.swagger.json-schema :refer [describe]]
            [schema.utils :as su]
            [reitit.ring.coercion :as rrc]
            [reitit.coercion.schema :as rcs]
            [schema-tools.core :as st]
            [ctia.auth.static :refer [->ReadOnlyIdentity ->WriteIdentity]]
            [reitit.ring.middleware.parameters :as parameters]
            [schema.core :as s]))

(def compojure->reitit-endpoints
  `{sut/GET :get
    sut/ANY :any
    sut/PATCH :patch
    sut/DELETE :delete
    sut/POST :post
    sut/PUT :put})

(defmacro with-deterministic-gensym [& body]
  `(with-bindings {#'sut/*gensym* (let [a# (atom -1)]
                                    (fn [s#]
                                      {:pre [(string? s#)]
                                       :post [(symbol? ~'%)]}
                                      (symbol (str s# "__" (swap! a# inc')))))}
     (do ~@body)))

(defn dexpand-1 [form]
  (with-deterministic-gensym
    (macroexpand-1 form)))

(deftest routes-test
  (is (= [["/blah" identity] ["/foo" identity]]
         (sut/routes ["/blah" identity] ["/foo" identity]))))

(deftest middleware-macro-test
  (is (= ["" {:middleware [[:some 1] [:middleware 2]]}
          [["/blah" identity] ["/foo" identity]]]
         (sut/middleware [[:some 1] [:middleware 2]]
           ["/blah" identity]
           ["/foo" identity])))
  (testing "200 response"
    (let [g (gensym)
          called (atom {:outer 0 :inner 0})]
      (is (= {:status 200 :body g}
             (let [mid (fn [handler]
                         (swap! called update :outer inc)
                         (fn
                           ([request]
                            (swap! called update :inner inc)
                            (handler
                              (assoc request ::middleware-called g)))
                           ([request respond raise]
                            (swap! called update :inner inc)
                            (handler
                              (assoc request ::middleware-called g)
                              respond
                              raise))))
                   app (ring/ring-handler
                         (ring/router
                           (sut/middleware
                             [mid]
                             (sut/GET
                               "/my-route" req
                               {:status 200
                                :body (::middleware-called req)}))))]
               (app {:request-method :get
                     :uri "/my-route"}))))
      ;; TODO why is :outer called twice?
      (is (= @called {:outer 2 :inner 1}))))
  )

(deftest middleware-restructure-test
  (testing "context"
    ;; could easily be supported if needed
    (is-banned-macro
      `(sut/context
         "/my-route" []
         :middleware [[~'render-resource-file]]
         ~'routes)
      "Not allowed these options in `context`, push into HTTP verbs instead: (:middleware)"))
  (testing "GET"
    (testing "expansion"
      (testing "combining with :capabilities is banned"
        (is-banned-macro
          `(sut/GET
             "/my-route" []
             :capabilities ~'capabilities
             :middleware [[~'render-resource-file]]
             ~'routes)
          "Combining :middleware and :capabilities not yet supported. Please use :middleware [(ctia.http.middleware.auth/wrap-capabilities capabilities)] instead of :capabilities capabilities.\nThe complete middleware might look like: :middleware (conj [[render-resource-file]] (ctia.http.middleware.auth/wrap-capabilities capabilities))."))
      (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0] (clojure.core/let [] (do identity)))
                                  :middleware [[render-resource-file]]}}]
             (dexpand-1
               `(sut/GET
                  "/my-route" []
                  :middleware [[~'render-resource-file]]
                  ~'identity)))))
    (testing "200 response"
      (let [g (gensym)
            called (atom {:outer 0 :inner 0})]
        (is (= {:status 200 :body g}
               (let [mid (fn [handler]
                           (swap! called update :outer inc)
                           (fn
                             ([request]
                              (swap! called update :inner inc)
                              (handler
                                (assoc request ::middleware-called g)))
                             ([request respond raise]
                              (swap! called update :inner inc)
                              (handler
                                (assoc request ::middleware-called g)
                                respond
                                raise))))
                     app (ring/ring-handler
                           (ring/router
                             (sut/GET
                               "/my-route" req
                               :middleware [mid]
                               {:status 200
                                :body (::middleware-called req)})))]
                 (app {:request-method :get
                       :uri "/my-route"}))))
        (is (= @called {:outer 1 :inner 1}))))))

;;FIXME runtime routing tests !!!!!!!!
(deftest context-test
  (is (= ["/my-route" [identity]]
         (sut/context
           "/my-route" []
           identity)))
  
  (is (= '["/my-route" {:middleware [[(ctia.http.middleware.auth/wrap-capabilities :create-incident)]]}
           (ctia.lib.compojure.api.core-reitit/routes clojure.core/identity)]
         (dexpand-1
           `(sut/context
              "/my-route" []
              :capabilities :create-incident
              identity))))
  (is (= '["/my-route" {:middleware [[(ctia.http.middleware.auth/wrap-capabilities capabilities-are-expressions)]]}
           (ctia.lib.compojure.api.core-reitit/routes clojure.core/identity)]
         (dexpand-1
           `(sut/context
              "/my-route" []
              :capabilities ~'capabilities-are-expressions
              identity))))
  
  
  (is (= ["/my-route"
          {:responses {200 {:body {:a (s/enum "schema")}}}}
          [identity]]
         (sut/context
           "/my-route" []
           :responses {200 {:schema {:a (s/enum "schema")}}}
           identity)))
  (is (= ["/my-route"
          {:responses {200 {:body {:a (s/enum "schema")}}}}
          [identity]]
         (let [responses-are-expressions {200 {:schema {:a (s/enum "schema")}}}]
           (sut/context
             "/my-route" []
             :responses responses-are-expressions
             identity)))))

(deftest get-test
  (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0] (clojure.core/let [] (do {:status 200})))}}]
         (dexpand-1
           `(sut/GET "/my-route" []
                     {:status 200}))))
  (is (= {:status 200
          :body "here"}
         (let [app (ring/ring-handler
                     (ring/router
                       (sut/GET "/my-route" []
                                {:status 200
                                 :body "here"})))]
           (app {:request-method :get
                 :uri "/my-route"})))))

(deftest responses-test
  (testing "GET"
    (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0] (clojure.core/let [] (do {:status 200, :body 1})))
                                :responses (ctia.lib.compojure.api.core-reitit/compojure->reitit-responses {200 {:schema schema.core/Int}})}}]
           (dexpand-1
             `(sut/GET "/my-route" []
                       :responses {200 {:schema s/Int}}
                       {:status 200
                        :body 1}))))
    (is (= {:status 200
            :body 1}
           (let [app (ring/ring-handler
                       (ring/router
                         (sut/GET "/my-route" []
                                  :responses {200 {:schema s/Int}}
                                  {:status 200
                                   :body 1})
                         {:data {:middleware [reitit.ring.coercion/coerce-response-middleware]
                                 :coercion reitit.coercion.schema/coercion}}))]
             (app {:request-method :get
                   :uri "/my-route"}))))
    (is (thrown? Exception "Response coercion failed"
                 (let [app (ring/ring-handler
                             (ring/router
                               (sut/GET "/my-route" []
                                        :responses {200 {:schema s/Bool}}
                                        {:status 200
                                         :body 1})
                               {:data {:middleware [reitit.ring.coercion/coerce-response-middleware]
                                       :coercion reitit.coercion.schema/coercion}}))]
                   (app {:request-method :get
                         :uri "/my-route"})))))
  (testing "context"
    (is (= '["/context" {:responses (ctia.lib.compojure.api.core-reitit/compojure->reitit-responses {200 {:schema schema.core/Int}})}
             (ctia.lib.compojure.api.core-reitit/routes
               (ctia.lib.compojure.api.core-reitit/GET "/my-route" [] {:status 200, :body 1}))]
           (dexpand-1
             `(sut/context "/context" []
                           :responses {200 {:schema s/Int}}
                           (sut/GET "/my-route" []
                                    {:status 200
                                     :body 1})))))
    (is (= {:status 200
            :body 1}
           (let [app (ring/ring-handler
                       (ring/router
                         (sut/context "/context" []
                                      :responses {200 {:schema s/Int}}
                                      (sut/GET "/my-route" []
                                               {:status 200
                                                :body 1}))
                         {:data {:middleware [reitit.ring.coercion/coerce-response-middleware]
                                 :coercion reitit.coercion.schema/coercion}}))]
             (app {:request-method :get
                   :uri "/context/my-route"}))))
    (is (thrown? Exception "Response coercion failed"
                 (let [app (ring/ring-handler
                             (ring/router
                               (sut/context "/context" []
                                            :responses {200 {:schema s/Bool}}
                                            (sut/GET "/my-route" []
                                                     {:status 200
                                                      :body 1}))
                               {:data {:middleware [reitit.ring.coercion/coerce-response-middleware]
                                       :coercion reitit.coercion.schema/coercion}}))]
                   (app {:request-method :get
                         :uri "/context/my-route"}))))))

(deftest capabilities-test
  (testing "expansion"
    (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0] (clojure.core/let [] (do {:status 200, :body 1})))
                                :middleware [[(ctia.http.middleware.auth/wrap-capabilities :create-incident)]]}}]
           (dexpand-1
             `(sut/GET "/my-route" []
                       :capabilities :create-incident
                       {:status 200
                        :body 1})))))

  (testing "GET"
    (testing "401 on no identity"
      (is (thrown-with-msg? Exception #"HTTP 401"
                            (let [app (ring/ring-handler
                                        (ring/router
                                          (sut/GET "/my-route" []
                                                   :capabilities :create-incident
                                                   {:status 200
                                                    :body 1})))]
                              (app {:request-method :get
                                    :uri "/my-route"})))))
    (testing "403 on bad capability"
      (is (thrown-with-msg? Exception #"HTTP 403"
                            (let [app (ring/ring-handler
                                        (ring/router
                                          (sut/GET "/my-route" []
                                                   :capabilities :create-incident
                                                   {:status 200
                                                    :body 1})))]
                              (app {:request-method :get
                                    :uri "/my-route"
                                    :identity (->ReadOnlyIdentity)})))))
    (testing "200 on good capability"
      (is (= {:status 200
              :body 1}
             (let [app (ring/ring-handler
                         (ring/router
                           (sut/GET "/my-route" []
                                    :capabilities :create-incident
                                    {:status 200
                                     :body 1})))]
               (app {:request-method :get
                     :uri "/my-route"
                     :identity (->WriteIdentity 'name 'group)}))))))
  (testing "context"
    (is (thrown-with-msg? Exception #"HTTP 401"
                          (let [app (ring/ring-handler
                                      (ring/router
                                        (sut/GET "/my-route" []
                                                 :capabilities :create-incident
                                                 {:status 200
                                                  :body 1})))]
                            (app {:request-method :get
                                  :uri "/my-route"}))))
    (testing "401 on no identity"
      (is (thrown-with-msg? Exception #"HTTP 401"
                            (let [app (ring/ring-handler
                                        (ring/router
                                          (sut/context "/foo" []
                                                       :capabilities :create-incident
                                                       (sut/GET "/my-route" []
                                                                {:status 200
                                                                 :body 1}))))]
                              (app {:request-method :get
                                    :uri "/foo/my-route"})))))
    (testing "403 on bad capability"
      (is (thrown-with-msg? Exception #"HTTP 403"
                            (let [app (ring/ring-handler
                                        (ring/router
                                          (sut/context "/foo" []
                                                       :capabilities :create-incident
                                                       (sut/GET "/my-route" []
                                                                {:status 200
                                                                 :body 1}))))]
                              (app {:request-method :get
                                    :uri "/foo/my-route"
                                    :identity (->ReadOnlyIdentity)})))))
    (testing "200 on good capability"
      (is (= {:status 200
              :body 1}
             (let [app (ring/ring-handler
                         (ring/router
                           (sut/context "/foo" []
                                        :capabilities :create-incident
                                        (sut/GET "/my-route" []
                                                 {:status 200
                                                  :body 1}))))]
               (app {:request-method :get
                     :uri "/foo/my-route"
                     :identity (->WriteIdentity 'name 'group)})))))))

(deftest auth-identity-test
  (testing "context"
    (is-banned-macro
      `(sut/context
         "/my-route" []
         :auth-identity ~'identity
         ~'routes)
      "Not allowed these options in `context`, push into HTTP verbs instead: (:auth-identity)"))
  (testing "GET"
    (testing "expansion"
      (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0]
                                             (clojure.core/let [identity__1 (:identity req__0)
                                                                scoped-identity identity__1]
                                               (do clojure.core/identity)))}}]
             (dexpand-1
               `(sut/GET
                  "/my-route" []
                  :auth-identity ~'scoped-identity
                  identity)))))
    (testing "200 response"
      (let [id (->ReadOnlyIdentity)
            response (let [app (ring/ring-handler
                                 (ring/router
                                   (sut/GET "/my-route" []
                                            :auth-identity scoped-identity
                                            {:status 200
                                             :body scoped-identity})))]
                       (app {:request-method :get
                             :uri "/my-route"
                             :identity id}))]
        (is (= {:status 200
                :body id}
               response))
        (is (identical? id (:body response)))))))

(deftest path-params-test
  (testing "context"
    (is-banned-macro
      `(sut/context
         "/my-route" []
         :path-params [~'id :- s/Str]
         ~'routes)
      "Not allowed these options in `context`, push into HTTP verbs instead: (:path-params)"))
  (testing "endpoints"
    (testing "expansion"
      (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0]
                                             (clojure.core/let [parameters__1 (:parameters req__0)
                                                                path__2 (:path parameters__1)
                                                                id (clojure.core/get path__2 :id)]
                                               (do identity)))
                                  :parameters {:path {:id schema.core/Str}}}}]
             (dexpand-1
               `(sut/GET
                  "/my-route" []
                  :path-params [~'id :- s/Str]
                  ~'identity)))))
    (testing "200 response"
      (testing "passes schema"
        (let [g (str (random-uuid))]
          (is (= {:status 200
                  :body g}
                 (let [app (ring/ring-handler
                             (ring/router
                               (sut/GET
                                 "/:id/foo" []
                                 :path-params [id :- s/Str]
                                 {:status 200
                                  :body id})
                               {:data {:middleware [parameters/parameters-middleware
                                                    rrc/coerce-request-middleware]
                                       :coercion reitit.coercion.schema/coercion}}))]
                   (app {:request-method :get
                         :uri (str "/" g "/foo")})))))
        (testing "in context"
          (let [g (str (random-uuid))]
            (is (= {:status 200
                    :body g}
                   (let [app (ring/ring-handler
                               (ring/router
                                 (sut/context
                                   "/:id" []
                                   (sut/GET
                                     "/foo" []
                                     :path-params [id :- s/Str]
                                     {:status 200
                                      :body id}))
                                 {:data {:middleware [parameters/parameters-middleware
                                                      rrc/coerce-request-middleware]
                                         :coercion reitit.coercion.schema/coercion}}))]
                     (app {:request-method :get
                           :uri (str "/" g "/foo")})))))))
      (testing "fails schema"
        (try (let [app (ring/ring-handler
                         (ring/router
                           (sut/GET
                             "/:id/foo" []
                             :path-params [id :- s/Int]
                             {:status 200
                              :body id})
                           {:data {:middleware [parameters/parameters-middleware
                                                rrc/coerce-request-middleware]
                                   :coercion reitit.coercion.schema/coercion}}))]
               (app {:request-method :get
                     :uri "/not-a-number/foo"}))
             (is false)
             (catch Exception e
               (let [actual (-> (ex-data e)
                                (select-keys [:errors :type])
                                (update :errors update-vals su/validation-error-explain))]
                 (is (= {:errors {:id '(not (integer? "not-a-number"))}
                         :type :reitit.coercion/request-coercion}
                        actual)))))
        (testing "in context"
          (try (let [app (ring/ring-handler
                           (ring/router
                             (sut/context
                               "/:id" []
                               (sut/GET
                                 "/foo" []
                                 :path-params [id :- s/Int]
                                 {:status 200
                                  :body id}))
                             {:data {:middleware [parameters/parameters-middleware
                                                  rrc/coerce-request-middleware]
                                     :coercion reitit.coercion.schema/coercion}}))]
                 (app {:request-method :get
                       :uri "/not-a-number/foo"}))
               (is false)
               (catch Exception e
                 (let [actual (-> (ex-data e)
                                  (select-keys [:errors :type])
                                  (update :errors update-vals su/validation-error-explain))]
                   (is (= {:errors {:id '(not (integer? "not-a-number"))}
                           :type :reitit.coercion/request-coercion}
                          actual))))))))))

(deftest query-params-test
  (testing "context"
    (is-banned-macro
      `(sut/context
         "/my-route" []
         :query-params [{~'wait_for :- (describe s/Bool "wait for patched entity to be available for search") nil}]
         ~'routes)
      "Not allowed these options in `context`, push into HTTP verbs instead: (:query-params)"))
  (testing "GET"
    (testing "expansion"
      (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0]
                                             (clojure.core/let [parameters__1 (:parameters req__0)
                                                                query__2 (:query parameters__1)
                                                                wait_for-default__3 default
                                                                wait_for (clojure.core/get query__2 :wait_for wait_for-default__3)]
                                               (do clojure.core/identity)))
                                  :parameters {:query (schema-tools.core/optional-keys
                                                        {:wait_for (ring.swagger.json-schema/describe
                                                                     schema.core/Bool
                                                                     "wait for patched entity to be available for search")})}}}]
             (dexpand-1
               `(sut/GET
                  "/my-route" []
                  :query-params [{~'wait_for :- (describe s/Bool "wait for patched entity to be available for search") ~'default}]
                  identity)))))
    (testing "200 response"
      (doseq [v [true false]]
        (is (= {:status 200
                :body v}
               (let [app (ring/ring-handler
                           (ring/router
                             (sut/GET
                               "/my-route" []
                               :query-params [{wait_for :- (describe s/Bool "wait for patched entity to be available for search") :default}]
                               {:status 200
                                :body wait_for})
                             {:data {:middleware [parameters/parameters-middleware
                                                  rrc/coerce-request-middleware]
                                     :coercion reitit.coercion.schema/coercion}}))]
                 (app {:request-method :get
                       :uri "/my-route"
                       :query-string (str "wait_for=" v)})))))
      (testing "default"
        (let [default (gensym)]
          (is (= {:status 200
                  :body default}
                 (let [app (ring/ring-handler
                             (ring/router
                               (sut/GET
                                 "/my-route" []
                                 :query-params [{wait_for :- (describe s/Bool "wait for patched entity to be available for search") default}]
                                 {:status 200
                                  :body wait_for})
                               {:data {:middleware [parameters/parameters-middleware
                                                    rrc/coerce-request-middleware]
                                     :coercion reitit.coercion.schema/coercion}}))]
                   (app {:request-method :get
                         :uri "/my-route"}))))))
      (testing "schema failure"
        (try (let [app (ring/ring-handler
                         (ring/router
                           (sut/GET
                             "/my-route" []
                             :query-params [{wait_for :- (describe s/Bool "wait for patched entity to be available for search") :default}]
                             {:status 200
                              :body wait_for})
                           {:data {:middleware [parameters/parameters-middleware
                                                rrc/coerce-request-middleware]
                                   :coercion reitit.coercion.schema/coercion}}))]
               (app {:request-method :get
                     :uri "/my-route"
                     :query-string "wait_for=1"}))
             (is false)
             (catch Exception e
               (let [actual (-> (ex-data e)
                                (select-keys [:errors :type])
                                (update :errors update-vals su/validation-error-explain))]
                 (is (= {:errors {:wait_for (list 'not (list 'instance? java.lang.Boolean "1"))}
                         :type :reitit.coercion/request-coercion}
                        actual)))))))))

(deftest identity-map-test
  (testing "context"
    (is-banned-macro
      `(sut/context
         "/my-route" []
         :identity-map ~'identity-map
         ~'routes)
      "Not allowed these options in `context`, push into HTTP verbs instead: (:identity-map)"))
  (testing "GET"
    (testing "expansion"
      (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0]
                                             (clojure.core/let [identity__1 (:identity req__0)
                                                                scoped-identity-map (ctia.auth/ident->map identity__1)]
                                               (do clojure.core/identity)))}}]
             (dexpand-1
               `(sut/GET
                  "/my-route" []
                  :identity-map ~'scoped-identity-map
                  identity))))
      (testing "with auth-identity, shares :identity"
        (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0]
                                               (clojure.core/let [identity__1 (:identity req__0)
                                                                  scoped-identity identity__1
                                                                  scoped-identity-map (ctia.auth/ident->map identity__1)]
                                                 (do clojure.core/identity)))}}]
               (dexpand-1
                 `(sut/GET
                    "/my-route" []
                    :identity-map ~'scoped-identity-map
                    :auth-identity ~'scoped-identity
                    identity))))))
    (testing "200 response"
      (let [id (->ReadOnlyIdentity)]
        (is (= {:status 200
                :body {:login "Unknown"
                       :groups ["Unknown Group"]
                       :client-id nil}}
               (let [app (ring/ring-handler
                           (ring/router
                             (sut/GET "/my-route" []
                                      :identity-map scoped-identity-map
                                      {:status 200
                                       :body scoped-identity-map})))]
                 (app {:request-method :get
                       :uri "/my-route"
                       :identity id}))))))))

(deftest return-test
  (testing "context"
    (is-banned-macro
      `(sut/context
         "/my-route" []
         :return s/Str
         ~'routes)
      (str ":return is banned, please use :responses instead.\n"
           "In this case, :return schema.core/Str is equivalent to :responses {200 {:schema schema.core/Str}}.\n"
           "For 204, you can use :responses {204 nil}.\nFor catch-all, use :responses {:default {:schema SCHEMA}}")))
  (testing "endpoints"
    (is-banned-macro
      `(sut/GET
         "/my-route" []
         :return s/Str
         ~'routes)
      (str ":return is banned, please use :responses instead.\n"
           "In this case, :return schema.core/Str is equivalent to :responses {200 {:schema schema.core/Str}}.\n"
           "For 204, you can use :responses {204 nil}.\nFor catch-all, use :responses {:default {:schema SCHEMA}}"))))

(deftest description-test
  (testing "context"
    (is (= ["/my-route"
            {:swagger {:description "a description"}}
            [identity]]
           (sut/context
             "/my-route" []
             :description "a description"
             identity)))
    (is (= ["/my-route"
            {:swagger {:description "a description"}}
            [identity]]
           (let [descriptions-are-expressions "a description"]
             (sut/context
               "/my-route" []
               :description descriptions-are-expressions
               identity)))))
  (testing "GET"
    (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0] (clojure.core/let [] (do identity)))
                                :swagger {:description "a description"}}}]
           (dexpand-1
             `(sut/GET
                "/my-route" []
                :description "a description"
                ~'identity))))
    (is (= {:description "a description"}
           (get-in (sut/GET
                     "/my-route" []
                     :description "a description"
                     ~'identity)
                   [1 :get :swagger])))))

(deftest tags-test
  (testing "context"
    (is (= ["/my-route"
            {:swagger {:tags #{:foo :bar}}}
            [identity]]
           (sut/context
             "/my-route" []
             :tags #{:foo :bar}
             identity)))
    ;; literals only to match compojure-api's semantics
    (is (= ["/my-route"
            {:swagger {:tags 'tags-are-compile-time-literals}}
            [identity]]
           (let [tags-are-compile-time-literals #{:foo :bar}]
             (sut/context
               "/my-route" []
               :tags tags-are-compile-time-literals
               identity)))))
  (testing "GET"
    (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0] (clojure.core/let [] (do identity)))
                                :swagger {:tags (quote tags-are-compile-time-literals)}}}]
           (dexpand-1
             `(sut/GET
                "/my-route" []
                :tags ~'tags-are-compile-time-literals
                ~'identity))))
    (is (= {:tags 'tags-are-compile-time-literals}
           (get-in (sut/GET
                     "/my-route" []
                     :tags tags-are-compile-time-literals
                     identity)
                   [1 :get :swagger])))))

(deftest no-doc-test
  (testing "context"
    ;; could easily be supported if needed
    (is-banned-macro
      `(sut/context
         "/my-route" []
         :no-doc ~'an-expression
         ~'routes)
      "Not allowed these options in `context`, push into HTTP verbs instead: (:no-doc)"))
  (testing "GET"
    (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0] (clojure.core/let [] (do identity)))
                                :swagger {:no-doc an-expression}}}]
           (dexpand-1
             `(sut/GET
                "/my-route" []
                :no-doc ~'an-expression
                ~'identity))))
    (testing "literals"
      (doseq [v [true false nil]]
        (is (= `["/my-route" {:get {:handler (clojure.core/fn [~'req__0] (clojure.core/let [] (do ~'identity)))
                                    :swagger {:no-doc ~v}}}]
               (dexpand-1
                 `(sut/GET
                    "/my-route" []
                    :no-doc ~v
                    ~'identity))))))
    (let [g (gensym)]
      (is (= {:no-doc g}
             (get-in (sut/GET
                       "/my-route" []
                       :no-doc g
                       ~'identity)
                     [1 :get :swagger]))))))

(deftest summary-test
  (testing "context"
    (is (= ["/my-route"
            {:swagger {:summary "a summary"}}
            [identity]]
           (sut/context
             "/my-route" []
             :summary "a summary"
             identity)))
  (is (= ["/my-route"
          {:swagger {:summary "a summary"}}
          [identity]]
         (let [summarys-are-expressions "a summary"]
           (sut/context
             "/my-route" []
             :summary summarys-are-expressions
             identity)))))
  (testing "GET"
    (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0] (clojure.core/let [] (do identity)))
                                :swagger {:summary an-expression}}}]
           (dexpand-1
             `(sut/GET
                "/my-route" []
                :summary ~'an-expression
                ~'identity))))
    (testing "literals"
      (doseq [v ["summary" true false nil]]
        (is (= `["/my-route" {:get {:handler (clojure.core/fn [~'req__0] (clojure.core/let [] (do ~'identity)))
                                    :swagger {:summary ~v}}}]
               (dexpand-1
                 `(sut/GET
                    "/my-route" []
                    :summary ~v
                    ~'identity))))))
    (let [g (gensym)]
      (is (= {:summary g}
             (get-in (sut/GET
                       "/my-route" []
                       :summary g
                       ~'identity)
                     [1 :get :swagger]))))))

(deftest produces-test
  (testing "context"
    ;; could easily be supported if needed
    (is-banned-macro
      `(sut/context
         "/my-route" []
         :produces ~'an-expression
         ~'routes)
      "Not allowed these options in `context`, push into HTTP verbs instead: (:produces)"))
  (testing "GET"
    (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0] (clojure.core/let [] (do identity)))
                                :swagger {:produces an-expression}}}]
           (dexpand-1
             `(sut/GET
                "/my-route" []
                :produces ~'an-expression
                ~'identity))))
    (testing "literals"
      (doseq [v ["produces" true false nil]]
        (is (= `["/my-route" {:get {:handler (clojure.core/fn [~'req__0] (clojure.core/let [] (do ~'identity)))
                                    :swagger {:produces ~v}}}]
               (dexpand-1
                 `(sut/GET
                    "/my-route" []
                    :produces ~v
                    ~'identity))))))
    (let [g (gensym)]
      (is (= {:produces g}
             (get-in (sut/GET
                       "/my-route" []
                       :produces g
                       ~'identity)
                     [1 :get :swagger]))))))

(deftest scoping-difference-banned-test
  (testing "context"
    (is-banned-macro
      `(sut/context
         "/my-route" ~'req
         ~'routes)
      "Not allowed to bind anything in context, push into HTTP verbs instead: req")
    (is-banned-macro
      `(sut/context
         "/my-route" ~'[req]
         ~'routes)
      "Not allowed to bind anything in context, push into HTTP verbs instead: [req]")
    (is-banned-macro
      `(sut/context
         "/my-route" ~'{:keys [req]}
         ~'routes)
      "Not allowed to bind anything in context, push into HTTP verbs instead: {:keys [req]}"))
  (testing "GET"
    (is-banned-macro
      `(sut/GET
         "/my-route" ~'req
         :middleware ~'req
         ~'routes)
      "There is a key difference in scoping between compojure-api and our compilation to reitit. The request has been bound to req but this symbol occurs in the restructuring options. The request is not in scope here in reitit, so please rename req so this incomplete analysis can rule out this mistake.")))

(deftest query-test
  (testing "context"
    (is-banned-macro
      `(sut/context
         "/my-route" []
         :query ~'[{foo :bar :as params}
                   Schema]
         ~'routes)
      "Not allowed these options in `context`, push into HTTP verbs instead: (:query)"))
  (testing "GET"
    (testing "expansion"
      (testing "missing schema"
        (is-banned-macro
          `(sut/GET
             "/my-route" []
             :query ~'[{foo :bar :as params}]
             ~'routes)
          ":query must be a vector of length 2"))
      (testing "cannot combine with :query-params"
        (is-banned-macro
          `(sut/GET
             "/my-route" []
             :query-params [{~'wait_for :- (describe s/Bool "wait for patched entity to be available for search") nil}]
             :query ~'[{foo :bar :as params}
                       Schema]
             ~'routes)
          "Cannot use both :query-params and :query, please combine them."))
      (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0]
                                             (clojure.core/let [parameters__1 (:parameters req__0)
                                                                query__2 (:query parameters__1)
                                                                {foo :bar :as params} query__2]
                                               (do routes)))
                                  :parameters {:query Schema}}}]
             (dexpand-1
               `(sut/GET
                  "/my-route" []
                  :query ~'[{foo :bar :as params}
                            Schema]
                  ~'routes))))
      (testing "200 response"
        (doseq [v [true false]]
          (is (= {:status 200
                  :body v}
                 (let [app (ring/ring-handler
                             (ring/router
                               (sut/GET
                                 "/my-route" []
                                 :query [{:keys [wait_for]}
                                         (st/optional-keys
                                           {:wait_for (describe s/Bool "wait for patched entity to be available for search")})]
                                 {:status 200
                                  :body wait_for})
                               {:data {:middleware [parameters/parameters-middleware
                                                    rrc/coerce-request-middleware]
                                       :coercion reitit.coercion.schema/coercion}}))]
                   (app {:request-method :get
                         :uri "/my-route"
                         :query-string (str "wait_for=" v)}))))))
      (testing "schema failure"
        (try (let [app (ring/ring-handler
                         (ring/router
                           (sut/GET
                             "/my-route" []
                             :query [{:keys [wait_for]}
                                     (st/optional-keys
                                       {:wait_for (describe s/Bool "wait for patched entity to be available for search")})]
                             {:status 200
                              :body wait_for})
                           {:data {:middleware [parameters/parameters-middleware
                                                rrc/coerce-request-middleware]
                                   :coercion reitit.coercion.schema/coercion}}))]
               (app {:request-method :get
                     :uri "/my-route"
                     :query-string "wait_for=1"}))
             (is false)
             (catch Exception e
               (let [actual (-> (ex-data e)
                                (select-keys [:errors :type])
                                (update :errors update-vals su/validation-error-explain))]
                 (is (= {:errors {:wait_for (list 'not (list 'instance? java.lang.Boolean "1"))}
                         :type :reitit.coercion/request-coercion}
                        actual)))))))))

(deftest body-test
  (testing "context"
    (is-banned-macro
      `(sut/context
         "/my-route" []
         :body ~'[l r]
         ~'routes)
       "Not allowed these options in `context`, push into HTTP verbs instead: (:body)"))
  (testing "endpoint"
    (is-banned-macro
      `(sut/GET
         "/my-route" []
         :body ~'[{foo :bar :as params}]
         ~'routes)
      ":body must be a vector of length 2")
    (is (= '["/my-route" {:get {:handler (clojure.core/fn [req__0]
                                           (clojure.core/let [parameters__1 (:parameters req__0)
                                                              body__2 (:body parameters__1)
                                                              {foo :bar :as body} body__2]
                                             (do routes)))
                                :parameters {:body Schema}}}]
           (dexpand-1
             `(sut/GET
                "/my-route" []
                :body ~'[{foo :bar :as body}
                         Schema]
                ~'routes))))
    (testing "200 response"
      (doseq [v [true false]]
        (is (= {:status 200
                :body v}
               (let [app (ring/ring-handler
                           (ring/router
                             (sut/GET
                               "/my-route" []
                               :body [{:keys [wait_for]}
                                       (st/optional-keys
                                         {:wait_for (describe s/Bool "wait for patched entity to be available for search")})]
                               {:status 200
                                :body wait_for})
                             {:data {:middleware [parameters/parameters-middleware
                                                  rrc/coerce-request-middleware]
                                     :coercion reitit.coercion.schema/coercion}}))]
                 (app {:request-method :get
                       :uri "/my-route"
                       :body-params {:wait_for v}}))))))
    (testing "schema failure"
      (try (let [app (ring/ring-handler
                       (ring/router
                         (sut/GET
                           "/my-route" []
                           :body [{:keys [wait_for]}
                                   (st/optional-keys
                                     {:wait_for (describe s/Bool "wait for patched entity to be available for search")})]
                           {:status 200
                            :body wait_for})
                         {:data {:middleware [parameters/parameters-middleware
                                              rrc/coerce-request-middleware]
                                 :coercion reitit.coercion.schema/coercion}}))]
             (app {:request-method :get
                   :uri "/my-route"
                   :body-params {:wait_for 1}}))
           (is false)
           (catch Exception e
             (let [actual (-> (ex-data e)
                              (select-keys [:errors :type])
                              (update :errors update-vals su/validation-error-explain))]
               (is (= {:errors {:wait_for (list 'not (list 'instance? java.lang.Boolean 1))}
                       :type :reitit.coercion/request-coercion}
                      actual))))))))
