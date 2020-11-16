(ns ctia.test-helpers.http
  (:require
   [clojure.string :as string]
   [clj-momo.test-helpers.http-assert-1 :as mthh]
   [ctia.lib.utils :refer [service-subgraph service-subgraph-from-schema]]
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

(defn assert-post [app & args]
  (apply (mthh/with-port-fn-and-api-key
           (partial th/get-http-port app)
           api-key
           mthh/assert-post)
         args))

(s/defn app->HTTPShowServices :- HTTPShowServices [app]
  (-> app
      app/service-graph
      (service-subgraph-from-schema HTTPShowServices)))

(s/defn app->APIHanderServices :- APIHandlerServices
  [app]
  (-> app
      app/service-graph
      (service-subgraph-from-schema APIHandlerServices)))
