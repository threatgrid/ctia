(ns ctia.entity.tool
  (:require
   [ctia.entity.tool.schemas :as ts]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.schemas.core :refer [APIHandlerServices]]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def tool-mapping
  {"tool"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:labels            em/token
      :kill_chain_phases em/kill-chain-phase
      :tool_version      em/token
      :x_mitre_aliases   em/token})}})

(def-es-store
  ToolStore
  :tool
  ts/StoredTool
  ts/PartialStoredTool)

(def tool-sort-fields
  (apply s/enum ts/tool-fields))

(s/defschema ToolFieldsParam
  {(s/optional-key :fields) [tool-sort-fields]})

(s/defschema ToolSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   ToolFieldsParam
   (st/optional-keys
    {:labels                            s/Str
     :kill_chain_phases.kill_chain_name s/Str
     :kill_chain_phases.phase_name      s/Str
     :tool_version                      s/Str
     :sort_by                           tool-sort-fields})))

(def tool-histogram-fields
  [:timestamp])

(def tool-enumerable-fields
  [:source
   :labels])

(s/defschema ToolGetParams ToolFieldsParam)

(s/defschema ToolByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   ToolFieldsParam))

(def capabilities
  #{:create-tool
    :read-tool
    :delete-tool
    :search-tool})

(s/defn tool-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :tool
    :new-schema               ts/NewTool
    :entity-schema            ts/Tool
    :get-schema               ts/PartialTool
    :get-params               ToolGetParams
    :list-schema              ts/PartialToolList
    :search-schema            ts/PartialToolList
    :external-id-q-params     ToolByExternalIdQueryParams
    :search-q-params          ToolSearchParams
    :new-spec                 :new-tool/map
    :realize-fn               ts/realize-tool
    :get-capabilities         :read-tool
    :post-capabilities        :create-tool
    :put-capabilities         :create-tool
    :delete-capabilities      :delete-tool
    :search-capabilities      :search-tool
    :external-id-capabilities :read-tool
    :can-aggregate?           true
    :histogram-fields         tool-histogram-fields
    :enumerable-fields        tool-enumerable-fields
    :searchable-fields        (routes.common/searchable-fields
                               ts/tool-fields)}))

(def tool-entity
  {:route-context         "/tool"
   :tags                  ["Tool"]
   :entity                :tool
   :plural                :tools
   :new-spec              :new-tool/map
   :schema                ts/Tool
   :partial-schema        ts/PartialTool
   :partial-list-schema   ts/PartialToolList
   :new-schema            ts/NewTool
   :stored-schema         ts/StoredTool
   :partial-stored-schema ts/PartialStoredTool
   :realize-fn            ts/realize-tool
   :es-store              ->ToolStore
   :es-mapping            tool-mapping
   :services->routes      (routes.common/reloadable-function tool-routes)
   :capabilities          capabilities
   :fields                ts/tool-fields
   :sort-fields           ts/tool-fields})
