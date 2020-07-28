(ns ctia.graphql-service-core
  (:require [puppetlabs.trapperkeeper.core :as tk]
            [ctia.graphql.schemas :as schemas]
            [ctia.schemas.graphql.helpers :as helpers]))

(defn get-graphql [{:keys [graphql]}]
  @graphql)

(defn get-or-update-type-registry [{:keys [type-registry]} name f]
  ;; FIXME seems prone to races, putting a lock here for now.
  ;; better solution might be swap! an intermediate value while
  ;; computing (f) so we don't block readers.
  (locking type-registry
    (or (get @type-registry name)
        (let [v (f)]
          (swap! type-registry assoc name v)
          v))))

(defn start [context services]
  (assoc context
         :type-registry (atom {})
         ;; delayed because :services contains GraphQLService operations,
         ;; which are only callable after this 'start' function returns.
         :graphql (delay
                    (-> schemas/graphql
                        (helpers/resolve-with-rt-opt
                          {:services services})))))
