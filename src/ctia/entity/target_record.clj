(ns ctia.entity.target-record
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.schemas.core :refer [def-acl-schema def-stored-schema]]
            [ctia.schemas.utils :as csu]
            [ctia.schemas.sorting :as sorting]
            [ctia.stores.es.mapping :as em]
            [ctia.stores.es.store :refer [def-es-store]]
            [ctim.schemas.target-record :as target-record-schema]
            [schema-tools.core :as st]
            [ctia.http.routes.crud :refer [entity-crud-routes]]
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
  (csu/optional-keys-schema StoredTargetRecord))

(def realize-target-record
  (default-realize-fn "target-record" NewTargetRecord StoredTargetRecord))

(def ^:private targets
  {:type "object"
   :properties
   {:type          em/all_token
    :observables   em/observable
    :os            em/all_token
    :internal      em/boolean-type
    :source_uri    em/all_token
    :observed_time em/valid-time
    :sensor        em/all_token}})

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
   TargetRecordFieldsParam
   (st/optional-keys
    {:query           s/Str
     :sort_by         target-record-sort-fields})))

(def target-record-histogram-fields
  [:timestamp
   :targets.observed_time.start_time
   :targets.observed_time.end_time])

(def target-record-routes
  (entity-crud-routes
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
    :enumerable-fields        target-record-enumerable-fields}))

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
   :services->routes      target-record-routes
   :capabilities          capabilities})
