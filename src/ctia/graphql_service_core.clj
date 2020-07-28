(ns ctia.graphql-service-core
  (:require [puppetlabs.trapperkeeper.core :as tk]
            [ctia.graphql.schemas :as schemas]
            [ctia.schemas.graphql.helpers :as helpers]))

(defn get-graphql [{:keys [graphql-promise]}]
  @graphql-promise)

(defn get-or-update-type-registry [type-registry name f]
  (or (some-> (get @type-registry name) deref)
      (let [[{oldprm name} {newprm name}] (swap-vals! type-registry
                                                      (fn [{oldprm name :as oldtr}]
                                                        (cond-> oldtr
                                                          (not oldprm)
                                                          (assoc name (promise)))))
            _ (assert (or oldprm newprm))]
        (when (not= oldprm newprm)
          (do (assert (not oldprm))
              (assert newprm)
              (deliver newprm (f))))
        @newprm)))

(defn init [context]
  (assoc context
         :type-registry (atom {})
         :graphql-promise (promise)))

(defn start [{:keys [graphql-promise type-registry] :as context} services]
  (deliver graphql-promise
           (-> schemas/graphql
               (helpers/resolve-with-rt-opt
                 {:services
                  (assoc-in services [:GraphQLService :get-or-update-type-registry]
                            #(get-or-update-type-registry type-registry %1 %2))})))
  context)
