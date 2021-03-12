(ns ctia.test-helpers.http
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.string :as string]
   [clj-momo.test-helpers.http :refer [post]]
   [clj-momo.test-helpers.http-assert-1 :as mthh]
   [ctia.lib.utils :refer [service-subgraph]]
   [ctia.schemas.core :refer [APIHandlerServices HTTPShowServices]]
   [ctia.test-helpers.core :as th]
   [puppetlabs.trapperkeeper.app :as app]
   [schema.core :as s]))

(def api-key "45c1f5e3f05d0")

(defn doc-id->rel-url
  "given a doc id (url) make a relative url for test queries"
  [doc-id]
  (when doc-id
    (string/replace doc-id #".*(?=ctia)" "")))

;; copied from ctia.test-helpers.http/assert-post to use Authorization header
;; instead of api-key
(defn assert-post*
  "Like test-post, but instead of using (is (= ...)), it only asserts
   that the status is 200.  Useful when the post is for test setup and
   the path is not the subject under test."
  [path port api-key new-entity]
  (let [{status :status
         result :parsed-body
         :as response}
        (post path
              port
              :body new-entity
              :headers {"Authorization" api-key})]
    (when (not= 201 status)
      (throw (ex-info (str "Expected status to be 201 but was " status
                           " for " path ":\n"
                           (with-out-str (pprint response)))
                      {:path path
                       :port port
                       :new-entity new-entity
                      :response response})))
    result))

(defn assert-post [app & args]
  (apply (mthh/with-port-fn-and-api-key
           (partial th/get-http-port app)
           api-key
           assert-post*)
         args))

(s/defn app->APIHandlerServices :- APIHandlerServices [app]
  (-> app
      app/service-graph
      ;; TODO use helper to select subgraph from schema
      (service-subgraph
        :ConfigService [:get-config
                        :get-in-config]
        :CTIAHTTPServerService [:get-port
                                :get-graphql]
        :HooksService [:apply-hooks
                       :apply-event-hooks ]
        :StoreService [:get-store]
        :IAuth [:identity-for-token]
        :GraphQLNamedTypeRegistryService [:get-or-update-named-type-registry]
        :IEncryption [:encrypt 
                      :decrypt]
        :FeaturesService [:enabled? 
                          :feature-flags])))

(s/defn app->HTTPShowServices :- HTTPShowServices [app]
  (-> app
      app/service-graph
      (service-subgraph
        :CTIAHTTPServerService [:get-port]
        :ConfigService [:get-in-config])))
