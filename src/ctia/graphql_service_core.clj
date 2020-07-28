(ns ctia.graphql-service-core
  (:require [puppetlabs.trapperkeeper.core :as tk]
            [ctia.graphql.schemas :as schemas]
            [ctia.schemas.graphql.helpers :as helpers])
  (:import [graphql GraphQL]))

(defn get-graphql [{:keys [graphql]}]
  {:post [(instance? GraphQL %)]}
  graphql)

;; :type-registry is an Atom<{String, Promise<graphql.*>}>
(defn get-or-update-type-registry [type-registry name f]
  {:post [%]}
  (or ;; fast-path for readers
      (some-> (get @type-registry name) deref)
      ;; might need to generate a value (or coordinate with another thread doing so)
      (let [[{oldprm name} {newprm name}] (swap-vals! type-registry
                                                      (fn [{oldprm name :as oldtr}]
                                                        (cond-> oldtr
                                                          (not oldprm)
                                                          (assoc name (promise)))))
            _ (assert (or oldprm newprm))]
        ;; if oldprm is nil, we're in charge of delivering newprm.
        ;; otherwise, another thread will deliver newprm.
        (when (nil? oldprm)
          (deliver newprm (f)))
        @newprm)))

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
