(ns ctia.schemas.graphql2
  (:require [ctia.schemas.graphql
             [common :as common]
             [helpers :as g]
             [judgement :refer [JudgementType
                                JudgementConnectionType]]
             [observable :refer [ObservableType]]
             [relationship :refer [RelationshipConnectionType]]
             [resolvers :refer [judgement-by-id
                                search-judgements
                                search-relationships]]]
            [schema.core :as s]
            [ctia.schemas.graphql.pagination :as p])
  (:import graphql.Scalars))

(s/defschema RelayGraphQLQuery
  {:query s/Str
   (s/optional-key :operationName) (s/maybe s/Str)
   (s/optional-key :variables) s/Any})

(s/defschema RelayGraphQLResponse
  {:data s/Any
   (s/optional-key :errors) [s/Any]})

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
    :judgements
    {:type JudgementConnectionType
     :args
     (merge
      common/lucene-query-arguments
      p/connection-arguments)
     :resolve search-judgements}
    :relationships
    {:type RelationshipConnectionType
     :args
     (merge
      common/lucene-query-arguments
      p/connection-arguments)
     :resolve search-relationships}
    :observable
    {:type ObservableType
     :args {:type {:type (g/non-null Scalars/GraphQLString)}
            :value {:type (g/non-null Scalars/GraphQLString)}}
     :resolve
     (fn [_ args _] args)}}))

(def schema (g/new-schema QueryType))
(def graphql (g/new-graphql schema))

(defn execute [query operation-name variables]
  (g/execute graphql query operation-name variables))
