(ns ctia.graphql.schemas
  (:require
   [ctia.entity.asset-mapping.graphql-schemas :as asset-mapping :refer [AssetMappingType AssetMappingConnectionType]]
   [ctia.entity.asset-properties.graphql-schemas :as asset-properties :refer [AssetPropertiesConnectionType]]
   [ctia.entity.asset.graphql-schemas :as asset :refer [AssetType AssetConnectionType]]
   [ctia.entity.attack-pattern :as attack-pattern :refer [AttackPatternConnectionType AttackPatternType]]
   [ctia.entity.casebook :as casebook :refer [CasebookConnectionType CasebookType]]
   [ctia.entity.incident :as incident :refer [IncidentConnectionType IncidentType]]
   [ctia.entity.indicator :as indicator :refer [IndicatorConnectionType IndicatorType]]
   [ctia.entity.investigation.graphql-schemas :as investigation :refer [InvestigationConnectionType InvestigationType]]
   [ctia.entity.judgement :as judgement :refer [JudgementConnectionType JudgementType]]
   [ctia.entity.malware :as malware :refer [MalwareConnectionType MalwareType]]
   [ctia.entity.sighting.graphql-schemas :as sighting :refer [SightingConnectionType SightingType]]
   [ctia.entity.tool.graphql-schemas :as tool :refer [ToolConnectionType ToolType]]
   [ctia.entity.vulnerability :as vulnerability :refer [VulnerabilityConnectionType VulnerabilityType]]
   [ctia.entity.weakness :as weakness :refer [WeaknessConnectionType WeaknessType]]
   [ctia.observable.graphql.schemas :as observable :refer [ObservableType]]
   [ctia.schemas.core :refer [APIHandlerServices RealizeFnResult]]
   [ctia.schemas.graphql.common :as common]
   [ctia.schemas.graphql.helpers :as g]
   [ctia.schemas.graphql.pagination :as p]
   [ctia.schemas.graphql.resolvers :as res]
   [schema.core :as s])
  (:import [graphql GraphQL Scalars]
           [graphql.schema
            GraphQLObjectType
            GraphQLSchema]))

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

(s/def QueryType :- (RealizeFnResult GraphQLObjectType)
  (g/new-object
   "Root"
   ""
   []
   {:asset            {:type    AssetType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :asset)}
    :assets           {:type    AssetConnectionType
                       :args    (merge common/lucene-query-arguments
                                       asset/asset-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :asset)}
    :asset_mapping    {:type    AssetMappingType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :asset-mapping)}
    :asset_mappings   {:type    AssetMappingConnectionType
                       :args    (merge common/lucene-query-arguments
                                       asset-mapping/asset-mapping-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :asset-mapping)}
    :asset_properties {:type    AssetPropertiesConnectionType
                       :args    (merge common/lucene-query-arguments
                                       asset-properties/asset-properties-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :asset-properties)}
    :attack_pattern   {:type    AttackPatternType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :attack-pattern)}
    :attack_patterns  {:type    AttackPatternConnectionType
                       :args    (merge common/lucene-query-arguments
                                       attack-pattern/attack-pattern-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :attack-pattern)}
    :casebook         {:type    CasebookType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :casebook)}
    :casebooks        {:type    CasebookConnectionType
                       :args    (merge common/lucene-query-arguments
                                       casebook/casebook-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :casebook)}
    :incident         {:type    IncidentType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :incident)}
    :incidents        {:type    IncidentConnectionType
                       :args    (merge common/lucene-query-arguments
                                       incident/incident-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :incident)}
    :indicator        {:type    IndicatorType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :indicator)}
    :indicators       {:type    IndicatorConnectionType
                       :args    (merge common/lucene-query-arguments
                                       indicator/indicator-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :indicator)}
    :investigation    {:type    InvestigationType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :investigation)}
    :investigations   {:type    InvestigationConnectionType
                       :args    (merge common/lucene-query-arguments
                                       investigation/investigation-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :investigation)}
    :judgement        {:type    JudgementType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :judgement)}
    :judgements       {:type    JudgementConnectionType
                       :args    (merge common/lucene-query-arguments
                                       judgement/judgement-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :judgement)}
    :malware          {:type    MalwareType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :malware)}
    :malwares         {:type    MalwareConnectionType
                       :args    (merge common/lucene-query-arguments
                                       malware/malware-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :malware)}
    :observable       {:type    ObservableType
                       :args    {:type  {:type (g/non-null Scalars/GraphQLString)}
                                 :value {:type (g/non-null Scalars/GraphQLString)}}
                       :resolve (fn [_ args _ _] args)}
    :sighting         {:type    SightingType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :sighting)}
    :sightings        {:type    SightingConnectionType
                       :args    (merge common/lucene-query-arguments
                                       sighting/sighting-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :sighting)}
    :tool             {:type    ToolType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :tool)}
    :tools            {:type    ToolConnectionType
                       :args    (merge common/lucene-query-arguments
                                       tool/tool-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :tool)}
    :vulnerability    {:type    VulnerabilityType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :vulnerability)}
    :vulnerabilities  {:type    VulnerabilityConnectionType
                       :args    (merge common/lucene-query-arguments
                                       vulnerability/vulnerability-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :vulnerability)}
    :weakness         {:type    WeaknessType
                       :args    search-by-id-args
                       :resolve (res/entity-by-id-resolver :weakness)}
    :weaknesses       {:type    WeaknessConnectionType
                       :args    (merge common/lucene-query-arguments
                                       weakness/weakness-order-arg
                                       p/connection-arguments)
                       :resolve (res/search-entity-resolver :weakness)}}))

(s/def schema :- (RealizeFnResult GraphQLSchema)
  (g/new-schema QueryType))
(s/def graphql :- (RealizeFnResult GraphQL)
  (g/new-graphql schema))

(s/defn execute [query
                 operation-name
                 variables
                 context
                 {{:keys [get-graphql]} :CTIAHTTPServerService} :- APIHandlerServices]
  (g/execute (get-graphql) query operation-name variables context))
