(ns ctia.graphql-named-type-registry-service
  (:require [ctia.graphql-named-type-registry-service-core :as core]
            [ctia.tk :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defprotocol GraphQLNamedTypeRegistryService
  (get-or-update-named-type-registry [this nme f]))

(tk/defservice graphql-named-type-registry-service
  GraphQLNamedTypeRegistryService
  []
  (start [_ context] (core/start context))
  (get-or-update-named-type-registry [this nme f] (core/get-or-update-named-type-registry
                                                    (service-context this)
                                                    nme
                                                    f)))
