(ns ctia.entity.incident-summary
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.store :refer :all]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams
                             PagingParams
                             SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [utils :as csu]
             [core :refer [def-stored-schema
                           CTIAEntity
                           CTIAStoredEntity]]
             [sorting :as sorting]]
            [ctia.schemas.graphql
             [sorting :as graphql-sorting]
             [flanders :as flanders]
             [helpers :as g]
             [pagination :as pagination]]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [ctim.schemas.incident-summary :as inc-sum]
            [flanders
             [schema :as f-schema]
             [spec :as f-spec]
             [utils :as fu]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defschema IncidentSummary
  (st/merge (f-schema/->schema inc-sum/IncidentSummary)
            CTIAEntity
            {s/Keyword s/Any}))

(f-spec/->spec inc-sum/IncidentSummary "incident-summary")

(s/defschema PartialIncidentSummary
  (st/merge (f-schema/->schema (fu/optionalize-all inc-sum/IncidentSummary))
            CTIAEntity
            {s/Keyword s/Any}))

(s/defschema PartialIncidentSummaryList
  [PartialIncidentSummary])

(s/defschema NewIncidentSummary
  (st/merge (f-schema/->schema inc-sum/NewIncidentSummary)
            CTIAEntity
            {s/Keyword s/Any}))

(f-spec/->spec inc-sum/NewIncidentSummary "new-incident-summary")

(def-stored-schema StoredIncidentSummary IncidentSummary)

(s/defschema PartialStoredIncidentSummary
  (csu/optional-keys-schema StoredIncidentSummary))

(def realize-incident-summary
  (default-realize-fn "incident-summary" NewIncidentSummary StoredIncidentSummary))

(def incident-summary-mapping
  {"incident-summary"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping)}})

(def-es-store IncidentSummaryStore :incident-summary
  StoredIncidentSummary
  PartialStoredIncidentSummary)

(def incident-summary-fields
  (concat sorting/default-entity-sort-fields
          sorting/describable-entity-sort-fields
          sorting/sourcable-entity-sort-fields))

(def incident-summary-sort-fields
  (apply s/enum incident-summary-fields))

(def incident-summary-select-fields
  (apply s/enum (concat incident-summary-fields
                        [:description
                         :type
                         :search-txt
                         :short_description
                         :created_at])))

(s/defschema IncidentSummaryFieldsParam
  {(s/optional-key :fields) [incident-summary-select-fields]})

(s/defschema IncidentSummarySearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   IncidentSummaryFieldsParam
   {:query s/Str}
   {s/Keyword s/Any}))

(def IncidentSummaryGetParams IncidentSummaryFieldsParam)

(s/defschema IncidentSummariesByExternalIdQueryParams
  (st/merge
   IncidentSummaryFieldsParam
   PagingParams))

(def IncidentSummaryType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all inc-sum/IncidentSummary)
         {})]
    (g/new-object
     name
     description
     []
     fields)))

(def incident-summary-order-arg
  (graphql-sorting/order-by-arg
   "IncidentSUmmaryOrder"
   "IncidentSummaries"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              incident-summary-fields))))

(def IncidentSummaryConnectionType
  (pagination/new-connection IncidentSummaryType))

(def incident-summary-routes
  (entity-crud-routes
   {:entity :incident-summary
    :new-schema NewIncidentSummary
    :entity-schema IncidentSummary
    :get-schema PartialIncidentSummary
    :get-params IncidentSummaryGetParams
    :list-schema PartialIncidentSummaryList
    :search-schema PartialIncidentSummaryList
    :external-id-q-params IncidentSummariesByExternalIdQueryParams
    :search-q-params IncidentSummarySearchParams
    :new-spec :new-incident-summary/map
    :realize-fn realize-incident-summary
    :get-capabilities :read-incident-summary
    :post-capabilities :create-incident-summary
    :put-capabilities :create-incident-summary
    :delete-capabilities :delete-incident-summary
    :search-capabilities :search-incident-summary
    :external-id-capabilities :read-incident-summary}))

(def capabilities
  #{:read-incident-summary
    :list-incident-summary
    :create-incident-summary
    :search-incident-summary
    :delete-incident-summary})

(def incident-summary-entity
  {:route-context "/incident-summary"
   :tags ["IncidentSummary"]
   :entity :incident-summary
   :plural :incident-summaries
   :new-spec :new-incident-summary/map
   :schema IncidentSummary
   :partial-schema PartialIncidentSummary
   :partial-list-schema PartialIncidentSummaryList
   :new-schema NewIncidentSummary
   :stored-schema StoredIncidentSummary
   :partial-stored-schema PartialStoredIncidentSummary
   :realize-fn realize-incident-summary
   :es-store ->IncidentSummaryStore
   :es-mapping incident-summary-mapping
   :routes incident-summary-routes
   :capabilities capabilities})
