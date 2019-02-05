(ns ctia.entity.investigation
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.store :refer :all]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
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
            [ctim.schemas.investigation :as inv]
            [flanders
             [schema :as f-schema]
             [spec :as f-spec]
             [utils :as fu]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defschema Investigation
  (st/merge (f-schema/->schema inv/Investigation)
            CTIAEntity
            {s/Keyword s/Any}))

(f-spec/->spec inv/Investigation "investigation")

(s/defschema PartialInvestigation
  (st/merge (f-schema/->schema (fu/optionalize-all inv/Investigation))
            CTIAEntity
            {s/Keyword s/Any}))

(s/defschema PartialInvestigationList
  [PartialInvestigation])

(s/defschema NewInvestigation
  (st/merge (f-schema/->schema inv/NewInvestigation)
            CTIAEntity
            {s/Keyword s/Any}))

(f-spec/->spec inv/NewInvestigation "new-investigation")

(def-stored-schema StoredInvestigation Investigation)

(s/defschema PartialStoredInvestigation
  (csu/optional-keys-schema StoredInvestigation))

(def realize-investigation
  (default-realize-fn "investigation" NewInvestigation StoredInvestigation))

(def investigation-mapping
  {"investigation"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping)}})

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
                         :created_at])))

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

(def InvestigationType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all inv/Investigation)
         {})]
    (g/new-object
     name
     description
     []
     fields)))

(def investigation-order-arg
  (graphql-sorting/order-by-arg
   "InvestigationOrder"
   "investigations"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              investigation-fields))))

(def InvestigationConnectionType
  (pagination/new-connection InvestigationType))

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
    :external-id-capabilities #{:read-investigation :external-id}}))

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
