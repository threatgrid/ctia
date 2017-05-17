(ns ctia.schemas.graphql2
  (:require [clojure.tools.logging :as log]
            [ctia.schemas.graphql.helpers :as g]
            [ctia.schemas.graphql.judgement
             :refer [JudgementType
                     judgement-by-id]]
            [ctia.schemas.graphql.observable
             :refer [ObservableType]])
  (:import graphql.Scalars))

(def QueryType
  (g/new-object
   "Root"
   ""
   []
   {:judgement
    {:type JudgementType
     :args {:id {:type (g/non-null Scalars/GraphQLString)}}
     :resolve
     (fn [_ args _] (judgement-by-id (:id args)))}
    :observable
    {:type ObservableType
     :args {:type {:type (g/non-null Scalars/GraphQLString)}
            :value {:type (g/non-null Scalars/GraphQLString)}}
     :resolve
     (fn [_ args _] args)}}))

(def schema (g/new-schema QueryType))
(def graphql (g/new-graphql schema))

(defn execute [query variables]
  (g/execute graphql query variables))
