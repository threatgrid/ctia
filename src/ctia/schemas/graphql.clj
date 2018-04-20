(ns ctia.schemas.graphql
  (:require
   [ctia.schemas.graphql
    [common :as common]
    [helpers :as g]
    [pagination :as p]
    [resolvers :as res]]
   [ctia.entity.attack-pattern :as attack-pattern
    :refer [AttackPatternConnectionType
            AttackPatternType]]
   [ctia.entity.indicator :as indicator
    :refer [IndicatorConnectionType
            IndicatorType]]
   [ctia.entity.investigation :as investigation
    :refer [InvestigationConnectionType
            InvestigationType]]
   [ctia.entity.casebook :as casebook
    :refer [CasebookConnectionType
            CasebookType]]
   [ctia.entity.judgement
    :as judgement
    :refer [JudgementConnectionType
            JudgementType]]
   [ctia.entity.malware :as malware
    :refer [MalwareConnectionType
            MalwareType]]
   [ctia.observable.graphql.schemas :as observable
    :refer [ObservableType]]
   [ctia.entity.sighting.graphql-schemas
    :as sighting
    :refer [SightingConnectionType
            SightingType]]
   [ctia.entity.tool.graphql-schemas :as tool
    :refer [ToolConnectionType ToolType]]
   [schema.core :as s])
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
