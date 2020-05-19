(ns ctia.entity.investigation
  (:require
   [ctia.http.routes
    [common :refer [BaseEntityFilterParams
                    PagingParams
                    SourcableEntityFilterParams]]
    [crud :refer [entity-crud-routes]]]
   [ctia.stores.es
    [mapping :as em]
    [store :refer [def-es-store]]]
   [ctia.entity.investigation.schemas
    :refer [Investigation
            PartialInvestigation
            PartialStoredInvestigation
            NewInvestigation
            StoredInvestigation
            PartialInvestigationList
            realize-investigation]]
   [schema-tools.core :as st]
   [schema.core :as s]
   [ctia.schemas.sorting :as sorting]))

(def snapshot-action-fields-mapping
  {:object_ids em/token
   :targets em/sighting-target
   :investigated_observables em/text})

(def investigation-mapping
  {"investigation"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     snapshot-action-fields-mapping)}})

(def-es-store InvestigationStore :investigation
  StoredInvestigation
  PartialStoredInvestigation)

(def investigation-fields
  (concat sorting/default-entity-sort-fields
          sorting/describable-entity-sort-fields
          sorting/sourcable-entity-sort-fields))

(def investigation-sort-fields
  (apply s/enum investigation-fields))

(def investigation-select-fields
  (apply s/enum (concat investigation-fields
                        [:description
                         :type
                         :search-txt
                         :short_description
                         :created_at
                         :object_ids
                         :investigated_observables
                         :targets])))

(s/defschema InvestigationFieldsParam
  {(s/optional-key :fields) [investigation-select-fields]})

(s/defschema InvestigationSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   InvestigationFieldsParam
   {:query s/Str}
   {s/Keyword s/Any}))

(def InvestigationGetParams InvestigationFieldsParam)

(s/defschema InvestigationsByExternalIdQueryParams
  (st/merge
   InvestigationFieldsParam
   PagingParams))

(def investigation-routes
  (entity-crud-routes
   {:entity :investigation
    :new-schema NewInvestigation
    :entity-schema Investigation
    :get-schema PartialInvestigation
    :get-params InvestigationGetParams
    :list-schema PartialInvestigationList
    :search-schema PartialInvestigationList
    :external-id-q-params InvestigationsByExternalIdQueryParams
    :search-q-params InvestigationSearchParams
    :new-spec :new-investigation/map
    :realize-fn realize-investigation
    :get-capabilities :read-investigation
    :post-capabilities :create-investigation
    :put-capabilities :create-investigation
    :delete-capabilities :delete-investigation
    :search-capabilities :search-investigation
    :external-id-capabilities :read-investigation}))

(def capabilities
  #{:read-investigation
    :list-investigations
    :create-investigation
    :search-investigation
    :delete-investigation})

(def investigation-entity
  {:route-context "/investigation"
   :tags ["Investigation"]
   :entity :investigation
   :plural :investigations
   :new-spec :new-investigation/map
   :schema Investigation
   :partial-schema PartialInvestigation
   :partial-list-schema PartialInvestigationList
   :new-schema NewInvestigation
   :stored-schema StoredInvestigation
   :partial-stored-schema PartialStoredInvestigation
   :realize-fn realize-investigation
   :es-store ->InvestigationStore
   :es-mapping investigation-mapping
   :routes investigation-routes
   :capabilities capabilities})
