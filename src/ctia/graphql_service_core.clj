(ns ctia.graphql-service-core
  (:require [puppetlabs.trapperkeeper.core :as tk]
            [ctia.graphql.schemas :as schemas]
            [ctia.schemas.core :refer [resolve-with-rt-opt]]
            [ctia.schemas.graphql.helpers :as helpers])
  (:import [graphql GraphQL]))

(defn get-graphql [{:keys [graphql]}]
  {:post [(instance? GraphQL %)]}
  graphql)

(defn start [context services]
  ;; Type registry to avoid any duplicates when using new-object
  ;; or new-enum. Contains a map with promises delivering graphql types, indexed by name
  (let [type-registry (atom {})]
    (assoc context
           :type-registry type-registry
           :graphql
           (-> schemas/graphql
               (resolve-with-rt-opt
                 {:services
                  (assoc-in services [:GraphQLService :get-or-update-named-type-registry]
                            #(helpers/get-or-update-named-type-registry type-registry %1 %2))})))))
