(ns ctia.entity.coa
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [utils :as csu]
             [core :refer [def-acl-schema def-stored-schema]]
             [sorting :refer [default-entity-sort-fields]]]
            [ctia.store :refer :all]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [ctim.schemas.coa :as coas]
            [flanders.utils :as fu]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def-acl-schema COA
  coas/COA
  "coa")

(def-acl-schema PartialCOA
  (fu/optionalize-all coas/COA)
  "partial-coa")

(s/defschema PartialCOAList
  [PartialCOA])

(def-acl-schema NewCOA
  coas/NewCOA
  "new-coa")

(def-stored-schema StoredCOA COA)

(s/defschema PartialStoredCOA
  (csu/optional-keys-schema StoredCOA))

(def realize-coa
  (default-realize-fn "coa" NewCOA StoredCOA))

(def coa-mapping
  {"coa"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:valid_time em/valid-time
      :stage em/token
      :coa_type em/token
      :objective em/text
      :impact em/token
      :cost em/token
      :efficacy em/token
      :structured_coa_type em/token
      :open_c2_coa em/open-c2-coa
      :related_COAs em/related-coas})}})

(def-es-store COAStore :coa StoredCOA PartialStoredCOA)

(def coa-fields
  (concat default-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time
           :stage
           :coa_type
           :impact
           :cost
           :efficacy
           :structured_coa_type]))

(def coa-sort-fields
  (apply s/enum coa-fields))

(s/defschema COAFieldsParam
  {(s/optional-key :fields) [coa-sort-fields]})

(s/defschema COASearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   COAFieldsParam
   {:query s/Str
    (s/optional-key :stage) s/Str
    (s/optional-key :coa_type) s/Str
    (s/optional-key :impact) s/Str
    (s/optional-key :objective) s/Str
    (s/optional-key :cost) s/Str
    (s/optional-key :efficacy) s/Str
    (s/optional-key :structured_coa_type) s/Str
    (s/optional-key :sort_by) coa-sort-fields}))

(def COAGetParams COAFieldsParam)

(s/defschema COAByExternalIdQueryParams
  (st/merge
   PagingParams
   COAFieldsParam))

(def coa-routes
  (entity-crud-routes
   {:entity :coa
    :new-schema NewCOA
    :entity-schema COA
    :get-schema PartialCOA
    :get-params COAGetParams
    :list-schema PartialCOAList
    :search-schema PartialCOAList
    :external-id-q-params COAByExternalIdQueryParams
    :search-q-params COASearchParams
    :new-spec :new-coa/map
    :realize-fn realize-coa
    :get-capabilities :read-coa
    :post-capabilities :create-coa
    :put-capabilities :create-coa
    :delete-capabilities :delete-coa
    :search-capabilities :search-coa
    :external-id-capabilities :read-coa}))

(def capabilities
  #{:create-coa
    :read-coa
    :delete-coa
    :search-coa})

(def coa-entity
  {:route-context "/coa"
   :tags ["COA"]
   :entity :coa
   :plural :coas
   :new-spec :new-coa/map
   :schema COA
   :partial-schema PartialCOA
   :partial-list-schema PartialCOAList
   :new-schema NewCOA
   :stored-schema StoredCOA
   :partial-stored-schema PartialStoredCOA
   :realize-fn realize-coa
   :es-store ->COAStore
   :es-mapping coa-mapping
   :routes coa-routes
   :capabilities capabilities})
