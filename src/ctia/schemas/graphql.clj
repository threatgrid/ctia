(ns ctia.schemas.graphql
  (:require [ctia.schemas.graphql
             [common :as common]
             [helpers :as g]
             [indicator :as indicator
              :refer [IndicatorType
                      IndicatorConnectionType]]
             [investigation :as investigation
              :refer [InvestigationType
                      InvestigationConnectionType]]
             [judgement :as judgement
              :refer [JudgementType
                      JudgementConnectionType]]
             [observable :refer [ObservableType]]
             [resolvers :as res]
             [sighting :as sighting
              :refer [SightingType
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
                :resolve (fn [context args _] (res/indicator-by-id
                                               (:id args)
                                               (:ident context)))}
    :indicators {:type IndicatorConnectionType
                 :args (merge common/lucene-query-arguments
                              indicator/indicator-order-arg
                              p/connection-arguments)
                 :resolve res/search-indicators}
    :investigation {:type InvestigationType
                    :args search-by-id-args
                    :resolve (fn [context args _] (res/investigation-by-id
                                                   (:id args)
                                                   (:ident context)))}
    :investigations {:type InvestigationConnectionType
                     :args (merge common/lucene-query-arguments
                                  investigation/investigation-order-arg
                                  p/connection-arguments)
                     :resolve res/search-investigations}
    :judgement {:type JudgementType
                :args search-by-id-args
                :resolve (fn [context args _]
                           (res/judgement-by-id (:id args)
                                                (:ident context)))}
    :judgements {:type JudgementConnectionType
                 :args (merge common/lucene-query-arguments
                              judgement/judgement-order-arg
                              p/connection-arguments)
                 :resolve res/search-judgements}
    :observable {:type ObservableType
                 :args {:type {:type (g/non-null Scalars/GraphQLString)}
                        :value {:type (g/non-null Scalars/GraphQLString)}}
                 :resolve (fn [_ args _] args)}
    :sighting {:type SightingType
               :args search-by-id-args
               :resolve (fn [context args _]
                          (res/sighting-by-id (:id args)
                                              (:ident context)))}
    :sightings {:type SightingConnectionType
                :args (merge common/lucene-query-arguments
                             sighting/sighting-order-arg
                             p/connection-arguments)
                :resolve res/search-sightings}}))

(def schema (g/new-schema QueryType))
(def graphql (g/new-graphql schema))

(defn execute [query operation-name variables context]
  (g/execute graphql query operation-name variables context))
