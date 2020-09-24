(ns ctia.graphql-service-core
  (:require [ctia.graphql.schemas :as schemas]
            [ctia.schemas.core :refer [resolve-with-rt-ctx
                                       RealizeFnServices]]
            [schema.core :as s])
  (:import [graphql GraphQL]))

(defn get-graphql [{:keys [graphql]}]
  {:post [(instance? GraphQL %)]}
  graphql)

(s/defn start [context
               services :- RealizeFnServices]
  (assoc context
         :graphql
         (-> schemas/graphql
             (resolve-with-rt-ctx
               {:services services}))))
