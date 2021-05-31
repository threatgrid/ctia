(ns ctia.entity.target-record
  (:require
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.schemas.core :refer [APIHandlerServices def-acl-schema def-stored-schema]]
   [ctia.schemas.sorting :as sorting]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
   [ctim.schemas.target-record :as target-record-schema]
   [schema-tools.core :as st]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.http.routes.common :as routes.common]
   [flanders.utils :as fu]
   [schema.core :as s]))

(def-acl-schema TargetRecord
  target-record-schema/TargetRecord
  "target-record")

(def-acl-schema PartialTargetRecord
  (fu/optionalize-all target-record-schema/TargetRecord)
  "partial-target-record")

(s/defschema PartialTargetRecordList
  [PartialTargetRecord])

(def-acl-schema NewTargetRecord
  target-record-schema/NewTargetRecord
  "new-target-record")

(def-stored-schema StoredTargetRecord TargetRecord)

(s/defschema PartialStoredTargetRecord
  (st/optional-keys-schema StoredTargetRecord))

(def realize-target-record
  (default-realize-fn "target-record" NewTargetRecord StoredTargetRecord))

(def ^:private targets
  {:type "object"
   :properties
   {:type          em/token
    :observables   em/observable
    :os            em/token
    :internal      em/boolean-type
    :source_uri    em/token
    :observed_time em/valid-time
    :sensor        em/token}})

(def target-record-mapping
  {"target-record"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:targets em/target-record-target})}})

(def-es-store TargetRecordStore :target-record StoredTargetRecord PartialStoredTargetRecord)

(def target-record-fields
  (concat
   sorting/base-entity-sort-fields
   sorting/sourcable-entity-sort-fields
   sorting/describable-entity-sort-fields
   [:targets.type
    :targets.os
    :targets.internal
    :targets.sensor]))

(def target-record-sort-fields
  (apply s/enum target-record-fields))

(s/defschema TargetRecordFieldsParam
  {(s/optional-key :fields) [target-record-sort-fields]})

(s/defschema TargetRecordByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   TargetRecordFieldsParam))

(def target-record-enumerable-fields
  [:source
   :targets.os
   :targets.internal
   :targets.observables.type])

(def TargetRecordGetParams TargetRecordFieldsParam)

(s/defschema TargetRecordSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   TargetRecordFieldsParam
   (st/optional-keys
    {:sort_by target-record-sort-fields})))

(def target-record-histogram-fields
  [:timestamp
   :targets.observed_time.start_time
   :targets.observed_time.end_time])

(s/defn target-record-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :target-record
    :new-schema               NewTargetRecord
    :entity-schema            TargetRecord
    :get-schema               PartialTargetRecord
    :get-params               TargetRecordGetParams
    :list-schema              PartialTargetRecordList
    :search-schema            PartialTargetRecordList
    :external-id-q-params     TargetRecordByExternalIdQueryParams
    :search-q-params          TargetRecordSearchParams
    :new-spec                 :new-target-record/map
    :realize-fn               realize-target-record
    :get-capabilities         :read-target-record
    :post-capabilities        :create-target-record
    :put-capabilities         :create-target-record
    :delete-capabilities      :delete-target-record
    :search-capabilities      :search-target-record
    :external-id-capabilities :read-target-record
    :can-aggregate?           true
    :histogram-fields         target-record-histogram-fields
    :enumerable-fields        target-record-enumerable-fields
    :searchable-fields        (routes.common/searchable-fields
                               target-record-fields)}))

(def capabilities
  #{:create-target-record
    :read-target-record
    :delete-target-record
    :search-target-record})

(def target-record-entity
  {:route-context         "/target-record"
   :tags                  ["Target Record"]
   :entity                :target-record
   :plural                :target-records
   :new-spec              :new-target-record/map
   :schema                TargetRecord
   :partial-schema        PartialTargetRecord
   :partial-list-schema   PartialTargetRecordList
   :new-schema            NewTargetRecord
   :stored-schema         StoredTargetRecord
   :partial-stored-schema PartialStoredTargetRecord
   :realize-fn            realize-target-record
   :es-store              ->TargetRecordStore
   :es-mapping            target-record-mapping
   :services->routes      (routes.common/reloadable-function
                            target-record-routes)
   :capabilities          capabilities
   :fields                target-record-fields
   :sort-fields           target-record-fields})
