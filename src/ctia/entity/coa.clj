(ns ctia.entity.coa
  (:require
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.schemas.core :refer [APIHandlerServices def-acl-schema def-stored-schema]]
   [ctia.schemas.sorting :refer [default-entity-sort-fields]]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
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
  (st/optional-keys-schema StoredCOA))

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
     {:valid_time          em/valid-time
      :stage               em/token
      :coa_type            em/token
      :objective           em/text
      :impact              em/token
      :cost                em/token
      :efficacy            em/token
      :structured_coa_type em/token
      :open_c2_coa         em/open-c2-coa
      :related_COAs        em/related-coas})}})

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
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   COAFieldsParam
   (st/optional-keys
    {:stage               s/Str
     :coa_type            s/Str
     :impact              s/Str
     :objective           s/Str
     :cost                s/Str
     :efficacy            s/Str
     :structured_coa_type s/Str
     :sort_by             coa-sort-fields})))

(def COAGetParams COAFieldsParam)

(s/defschema COAByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   COAFieldsParam))

(def coa-histogram-fields
  [:timestamp
   :valid_time.start_time
   :valid_time.end_time])

(def coa-enumerable-fields
  [:source
   :efficacy
   :cost
   :coa_type])

(s/defn coa-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :coa
    :new-schema               NewCOA
    :entity-schema            COA
    :get-schema               PartialCOA
    :get-params               COAGetParams
    :list-schema              PartialCOAList
    :search-schema            PartialCOAList
    :external-id-q-params     COAByExternalIdQueryParams
    :search-q-params          COASearchParams
    :new-spec                 :new-coa/map
    :realize-fn               realize-coa
    :get-capabilities         :read-coa
    :post-capabilities        :create-coa
    :put-capabilities         :create-coa
    :delete-capabilities      :delete-coa
    :search-capabilities      :search-coa
    :external-id-capabilities :read-coa
    :can-aggregate?           true
    :histogram-fields         coa-histogram-fields
    :enumerable-fields        coa-enumerable-fields
    :searchable-fields        (routes.common/searchable-fields
                               coa-fields)}))

(def capabilities
  #{:create-coa
    :read-coa
    :delete-coa
    :search-coa})

(def coa-entity
  {:route-context         "/coa"
   :tags                  ["COA"]
   :entity                :coa
   :plural                :coas
   :new-spec              :new-coa/map
   :schema                COA
   :partial-schema        PartialCOA
   :partial-list-schema   PartialCOAList
   :new-schema            NewCOA
   :stored-schema         StoredCOA
   :partial-stored-schema PartialStoredCOA
   :realize-fn            realize-coa
   :es-store              ->COAStore
   :es-mapping            coa-mapping
   :services->routes      (routes.common/reloadable-function coa-routes)
   :capabilities          capabilities
   :fields                coa-fields
   :sort-fields           coa-fields})
