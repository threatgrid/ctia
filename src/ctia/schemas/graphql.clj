(ns ctia.schemas.graphql
  (:require [ctia.schemas.graphql
             [common :as common]
             [helpers :as g]
             [attack-pattern :as attack-pattern
              :refer [AttackPatternType
                      AttackPatternConnectionType]]
             [indicator :as indicator
              :refer [IndicatorType
                      IndicatorConnectionType]]
             [investigation :as investigation
              :refer [InvestigationType
                      InvestigationConnectionType]]
             [casebook :as casebook
              :refer [CasebookType
                      CasebookConnectionType]]
             [judgement :as judgement
              :refer [JudgementType
                      JudgementConnectionType]]
             [malware :as malware
              :refer [MalwareType
                      MalwareConnectionType]]
             [observable :refer [ObservableType]]
             [resolvers :as res]
             [sighting :as sighting
              :refer [SightingType
                      SightingConnectionType]]
             [tool :as tool
              :refer [ToolType
                      ToolConnectionType]]]
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
   {:attack_pattern {:type AttackPatternType
                     :args search-by-id-args
                     :resolve (res/entity-by-id-resolver :attack-pattern)}
    :attack_patterns {:type AttackPatternConnectionType
                      :args (merge common/lucene-query-arguments
                                   attack-pattern/attack-pattern-order-arg
                                   p/connection-arguments)
                      :resolve (res/search-entity-resolver :attack-pattern)}
    :indicator {:type IndicatorType
                :args search-by-id-args
                :resolve (res/entity-by-id-resolver :indicator)}
    :indicators {:type IndicatorConnectionType
                 :args (merge common/lucene-query-arguments
                              indicator/indicator-order-arg
                              p/connection-arguments)
                 :resolve (res/search-entity-resolver :indicator)}
    :investigation {:type InvestigationType
                    :args search-by-id-args
                    :resolve (res/entity-by-id-resolver :investigation)}
    :investigations {:type InvestigationConnectionType
                     :args (merge common/lucene-query-arguments
                                  investigation/investigation-order-arg
                                  p/connection-arguments)
                     :resolve (res/search-entity-resolver :investigation)}
    :judgement {:type JudgementType
                :args search-by-id-args
                :resolve (res/entity-by-id-resolver :judgement)}
    :judgements {:type JudgementConnectionType
                 :args (merge common/lucene-query-arguments
                              judgement/judgement-order-arg
                              p/connection-arguments)
                 :resolve (res/search-entity-resolver :judgement)}
    :malware {:type MalwareType
              :args search-by-id-args
              :resolve (res/entity-by-id-resolver :malware)}
    :malwares {:type MalwareConnectionType
               :args (merge common/lucene-query-arguments
                            malware/malware-order-arg
                            p/connection-arguments)
               :resolve (res/search-entity-resolver :malware)}
    :observable {:type ObservableType
                 :args {:type {:type (g/non-null Scalars/GraphQLString)}
                        :value {:type (g/non-null Scalars/GraphQLString)}}
                 :resolve (fn [_ args _ _] args)}
    :casebook {:type CasebookType
               :args search-by-id-args
               :resolve (res/entity-by-id-resolver :casebook)}
    :casebooks {:type CasebookConnectionType
                :args (merge common/lucene-query-arguments
                             casebook/casebook-order-arg
                             p/connection-arguments)
                :resolve (res/search-entity-resolver :casebook)}
    :sighting {:type SightingType
               :args search-by-id-args
               :resolve (res/entity-by-id-resolver :sighting)}
    :sightings {:type SightingConnectionType
                :args (merge common/lucene-query-arguments
                             sighting/sighting-order-arg
                             p/connection-arguments)
                :resolve (res/search-entity-resolver :sighting)}
    :tool {:type ToolType
           :args search-by-id-args
           :resolve (res/entity-by-id-resolver :tool)}
    :tools {:type ToolConnectionType
            :args (merge common/lucene-query-arguments
                         tool/tool-order-arg
                         p/connection-arguments)
            :resolve (res/search-entity-resolver :tool)}}))

(def schema (g/new-schema QueryType))
(def graphql (g/new-graphql schema))

(defn execute [query operation-name variables context]
  (g/execute graphql query operation-name variables context))
