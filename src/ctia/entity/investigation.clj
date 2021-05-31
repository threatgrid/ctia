(ns ctia.entity.investigation
  (:require
   [ctia.entity.investigation.schemas :as inv]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.schemas.core :refer [APIHandlerServices]]
   [ctia.schemas.sorting :as sorting]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def snapshot-action-fields-mapping
  {:object_ids               em/token
   :targets                  em/sighting-target
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
  inv/StoredInvestigation
  inv/PartialStoredInvestigation)

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
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   InvestigationFieldsParam))

(def InvestigationGetParams InvestigationFieldsParam)

(s/defschema InvestigationsByExternalIdQueryParams
  (st/merge
   InvestigationFieldsParam
   routes.common/PagingParams))

(def investigation-enumerable-fields
  [:source])

(def investigation-histogram-fields
  [:timestamp])

(s/defn investigation-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :investigation
    :new-schema               inv/NewInvestigation
    :entity-schema            inv/Investigation
    :get-schema               inv/PartialInvestigation
    :get-params               InvestigationGetParams
    :list-schema              inv/PartialInvestigationList
    :search-schema            inv/PartialInvestigationList
    :external-id-q-params     InvestigationsByExternalIdQueryParams
    :search-q-params          InvestigationSearchParams
    :new-spec                 :new-investigation/map
    :realize-fn               inv/realize-investigation
    :get-capabilities         :read-investigation
    :post-capabilities        :create-investigation
    :put-capabilities         :create-investigation
    :delete-capabilities      :delete-investigation
    :search-capabilities      :search-investigation
    :external-id-capabilities :read-investigation
    :can-aggregate?           true
    :histogram-fields         investigation-histogram-fields
    :enumerable-fields        investigation-enumerable-fields
    :searchable-fields        (routes.common/searchable-fields
                               investigation-fields)}))

(def capabilities
  #{:read-investigation
    :list-investigations
    :create-investigation
    :search-investigation
    :delete-investigation})

(def investigation-entity
  {:route-context         "/investigation"
   :tags                  ["Investigation"]
   :entity                :investigation
   :plural                :investigations
   :new-spec              :new-investigation/map
   :schema                inv/Investigation
   :partial-schema        inv/PartialInvestigation
   :partial-list-schema   inv/PartialInvestigationList
   :new-schema            inv/NewInvestigation
   :stored-schema         inv/StoredInvestigation
   :partial-stored-schema inv/PartialStoredInvestigation
   :realize-fn            inv/realize-investigation
   :es-store              ->InvestigationStore
   :es-mapping            investigation-mapping
   :services->routes      (routes.common/reloadable-function investigation-routes)
   :capabilities          capabilities
   :fields                investigation-fields
   :sort-fields           investigation-fields})
