(ns ctia.schemas.graphql
  (:require [ctia.schemas.graphql
             [common :as common]
             [helpers :as g]
             [indicator :refer [IndicatorType
                                IndicatorConnectionType]]
             [judgement :refer [JudgementType
                                JudgementConnectionType]]
             [observable :refer [ObservableType]]
             [resolvers :as res]
             [sighting :refer [SightingType
                               SightingConnectionType]]]
            [schema.core :as s]
            [ctia.schemas.graphql.pagination :as p])
  (:import graphql.Scalars))

;; TODO
;; Sorting : https://github.com/graphql/graphql-relay-js/issues/20

(s/defschema RelayGraphQLQuery
  {:query s/Str
   (s/optional-key :operationName) (s/maybe s/Str)
   (s/optional-key :variables) s/Any})

(s/defschema RelayGraphQLResponse
  {:data s/Any
   (s/optional-key :errors) [s/Any]})

(def search-by-id-args
  {:id {:type (g/non-null Scalars/GraphQLString)}})

(def QueryType
  (g/new-object
   "Root"
   ""
   []
   {:indicator {:type IndicatorType
                :args search-by-id-args
                :resolve (fn [_ args _] (res/indicator-by-id (:id args)))}
    :indicators {:type IndicatorConnectionType
                 :args (into common/lucene-query-arguments
                             p/connection-arguments)
                 :resolve res/search-indicators}
    :judgement {:type JudgementType
                :args search-by-id-args
                :resolve (fn [_ args _] (res/judgement-by-id (:id args)))}
    :judgements {:type JudgementConnectionType
                 :args (into common/lucene-query-arguments
                             p/connection-arguments)
                 :resolve res/search-judgements}
    :observable {:type ObservableType
                 :args {:type {:type (g/non-null Scalars/GraphQLString)}
                        :value {:type (g/non-null Scalars/GraphQLString)}}
                 :resolve (fn [_ args _] args)}
    :sighting {:type SightingType
               :args search-by-id-args
               :resolve (fn [_ args _] (res/sighting-by-id (:id args)))}
    :sightings {:type SightingConnectionType
                :args (into common/lucene-query-arguments
                            p/connection-arguments)
                :resolve res/search-sightings}}))

(def schema (g/new-schema QueryType))
(def graphql (g/new-graphql schema))

(defn execute [query operation-name variables]
  (g/execute graphql query operation-name variables))
