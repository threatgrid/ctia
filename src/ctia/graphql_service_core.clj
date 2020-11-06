(ns ctia.graphql-service-core
  (:require [ctia.graphql.schemas :as schemas]
            [ctia.graphql-service-schemas :refer [Context]]
            [ctia.schemas.core :refer [resolve-with-rt-ctx
                                       RealizeFnServices]]
            [schema.core :as s])
  (:import [graphql GraphQL]))

(s/defn get-graphql :- GraphQL
  [{:keys [graphql]} :- Context]
  graphql)

(s/defn start [context
               services :- RealizeFnServices]
  (assoc context
         :graphql
         (-> schemas/graphql
             (resolve-with-rt-ctx
               {:services services}))))
