(ns ctia.graphql-service-core
  (:require [puppetlabs.trapperkeeper.core :as tk]
            [ctia.graphql.schemas :as schemas]
            [ctia.schemas.graphql.helpers :as helpers])
  (:import [graphql GraphQL]))

(defn get-graphql [{:keys [graphql]}]
  {:post [(instance? GraphQL %)]}
  graphql)

;; :type-registry is an Atom<{String, IDeref<graphql.*>}>
(defn get-or-update-type-registry
  "If name exists in registry, return existing mapping. Otherwise conj
  {name (f)} to registry, and return (f)."
  [type-registry name f]
  {:post [%]}
  (or ;; fast-path for readers
      (some-> (get @type-registry name) deref)
      ;; generate a new graphql value, or coordinate with another thread doing the same
      (let [f (bound-fn* f) ;; may be run on another thread
            {result-delay name} (swap! type-registry
                                       (fn [{existing-delay name :as oldtr}]
                                         (cond-> oldtr
                                           (not existing-delay)
                                           (assoc name (delay (f))))))]
        @result-delay)))

(defn start [context services]
  ;; Type registry to avoid any duplicates when using new-object
  ;; or new-enum. Contains a map with promises delivering graphql types, indexed by name
  (let [type-registry (atom {})]
    (assoc context
           :type-registry type-registry
           :graphql
           (-> schemas/graphql
               (helpers/resolve-with-rt-opt
                 {:services
                  (assoc-in services [:GraphQLService :get-or-update-type-registry]
                            #(get-or-update-type-registry type-registry %1 %2))})))))
