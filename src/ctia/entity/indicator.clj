(ns ctia.entity.indicator
  (:require
   [ctia.entity.feedback.graphql-schemas :as feedback]
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.entity.relationship.graphql-schemas :as relationship]
   [ctia.http.routes
    [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
    [crud :refer [entity-crud-routes]]]
   [ctia.schemas
    [core :refer [ACLEntity
                  ACLStoredEntity]]
    [sorting :as sorting]]
   [ctia.schemas.graphql
    [sorting :as graphql-sorting]
    [flanders :as f]
    [helpers :as g]
    [pagination :as pagination]
    [refs :as refs]]
   [ctia.stores.es
    [mapping :as em]
    [store :refer [def-es-store]]]
   [ctim.schemas.indicator :as ins]
   [flanders
    [schema :as f-schema]
    [spec :as f-spec]
    [utils :as fu]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema Indicator
  (st/merge ACLEntity
            (f-schema/->schema
             (fu/replace-either-with-any
              ins/Indicator))))

(f-spec/->spec ins/Indicator "indicator")

(s/defschema PartialIndicator
  (st/merge ACLEntity
            (f-schema/->schema
             (fu/optionalize-all
              (fu/replace-either-with-any
               ins/Indicator)))))

(s/defschema PartialIndicatorList
  [PartialIndicator])

(s/defschema NewIndicator
  (st/merge
   (f-schema/->schema
    (fu/replace-either-with-any
     ins/NewIndicator))
   ACLEntity))

(f-spec/->spec ins/NewIndicator "new-indicator")

(s/defschema StoredIndicator
  (st/merge (f-schema/->schema
             (fu/replace-either-with-any
              ins/StoredIndicator))
            ACLStoredEntity))

(f-spec/->spec ins/StoredIndicator "stored-indicator")

(s/defschema PartialStoredIndicator
  (st/merge (f-schema/->schema
             (fu/optionalize-all
              (fu/replace-either-with-any
               ins/StoredIndicator)))
            ACLStoredEntity))

(def realize-indicator
  (default-realize-fn "indicator" NewIndicator StoredIndicator))

(def indicator-mapping
  {"indicator"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:valid_time em/valid-time
      :producer em/token
      :negate {:type "boolean"}
      :indicator_type em/token
      :alternate_ids em/token
      :tags em/all_token
      :composite_indicator_expression {:type "object"
                                       :properties
                                       {:operator em/token
                                        :indicator_ids em/token}}
      :likely_impact em/token
      :confidence em/token
      :kill_chain_phases em/kill-chain-phase
      :test_mechanisms em/token
      :specification {:enabled false}})}})

(def-es-store IndicatorStore :indicator StoredIndicator PartialStoredIndicator)

(def indicator-fields
  (concat sorting/default-entity-sort-fields
          [:indicator_type
           :likely_impact
           :confidence]))

(def indicator-sort-fields
  (apply s/enum indicator-fields))

(s/defschema IndicatorFieldsParam
  {(s/optional-key :fields) [indicator-sort-fields]})

(s/defschema IndicatorSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   IndicatorFieldsParam
   {:query s/Str
    (s/optional-key :indicator_type) s/Str
    (s/optional-key :tags) s/Int
    (s/optional-key :kill_chain_phases) s/Str
    (s/optional-key :producer) s/Str
    (s/optional-key :specification) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :sort_by)  indicator-sort-fields}))

(def IndicatorGetParams IndicatorFieldsParam)

(s/defschema IndicatorsListQueryParams
  (st/merge
   PagingParams
   IndicatorFieldsParam
   {(s/optional-key :sort_by) indicator-sort-fields}))

(s/defschema IndicatorsByExternalIdQueryParams
  IndicatorsListQueryParams)

(def indicator-routes
  (entity-crud-routes
   {:entity :indicator
    :new-schema NewIndicator
    :entity-schema Indicator
    :get-schema PartialIndicator
    :get-params IndicatorGetParams
    :list-schema PartialIndicatorList
    :search-schema PartialIndicatorList
    :external-id-q-params IndicatorsByExternalIdQueryParams
    :search-q-params IndicatorSearchParams
    :new-spec :new-indicator/map
    :realize-fn realize-indicator
    :get-capabilities :read-indicator
    :post-capabilities :create-indicator
    :put-capabilities :create-indicator
    :delete-capabilities :delete-indicator
    :search-capabilities :search-indicator
    :external-id-capabilities #{:read-indicator :external-id}}))

(def capabilities
  #{:read-indicator
    :list-indicators
    :create-indicator
    :search-indicator
    :delete-indicator})

(def IndicatorType
  (let [{:keys [fields name description]}
        (f/->graphql
         (fu/optionalize-all ins/Indicator)
         {refs/related-judgement-type-name relationship/RelatedJudgement
          refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object name description []
                  (merge fields
                         feedback/feedback-connection-field
                         relationship/relatable-entity-fields))))

(def indicator-order-arg
  (graphql-sorting/order-by-arg
   "IndicatorOrder"
   "indicators"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              indicator-fields))))

(def IndicatorConnectionType
  (pagination/new-connection IndicatorType))

(def indicator-entity
  {:route-context "/indicator"
   :tags ["Indicator"]
   :entity :indicator
   :plural :indicators
   :schema Indicator
   :partial-schema PartialIndicator
   :partial-list-schema PartialIndicatorList
   :new-schema NewIndicator
   :stored-schema StoredIndicator
   :partial-stored-schema PartialStoredIndicator
   :realize-fn realize-indicator
   :es-store ->IndicatorStore
   :es-mapping indicator-mapping
   :routes indicator-routes
   :capabilities capabilities})
