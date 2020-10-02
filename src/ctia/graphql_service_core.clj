(ns ctia.graphql-service-core
  (:require [ctia.graphql.schemas :as schemas]
            [ctia.schemas.core :refer [resolve-with-rt-ctx
                                       RealizeFnServices]]
            [ctia.http.server-service :as server-svc]
            [schema.core :as s]
            [schema-tools.core :as st])
  (:import [graphql GraphQL]))

(defn get-graphql [{:keys [graphql-delay]}]
  {:post [(instance? GraphQL %)]}
  @graphql-delay)

(s/defn start [context
               get-port
               services :- (st/dissoc-in RealizeFnServices [:CTIAHTTPServerService :get-port])]
  (assoc context
         :graphql-delay
         (delay
           (let [services (-> services
                              ;; tie the knot between CTIAHTTPServerService and GraphQLService
                              (assoc-in [:CTIAHTTPServerService :get-port] get-port))]
             (-> schemas/graphql
                 (resolve-with-rt-ctx
                   {:services services}))))))
