(ns ctia.graphql-service-core
  (:require [puppetlabs.trapperkeeper.core :as tk]
            [ctia.graphql.schemas :as schemas]
            [ctia.schemas.graphql.helpers :as helpers]))

(defn get-graphql [{:keys [graphql]}]
  @graphql)

(defn get-type-registry [{:keys [type-registry]}]
  type-registry)

(defn start [context services]
  (assoc context
         ;; TODO investigate race conditions
         :type-registry (atom {})
         ;; delayed because :services also contains GraphQLService operations,
         ;; which is only ready to call after this 'start' function returns.
         :graphql (delay
                    (-> schemas/graphql
                        (helpers/resolve-with-rt-opt
                          {:services services})))))
