(ns ctia.entity.tool
  (:require [ctia.entity.tool.schemas :as ts]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.store :refer :all]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def tool-mapping
  {"tool"
   {:dynamic false
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:name em/all_token
      :description em/all_text
      :labels em/token
      :kill_chain_phases em/kill-chain-phase
      :tool_version em/token
      :x_mitre_aliases em/token})}})

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
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   ToolFieldsParam
   {:query s/Str}
   (st/optional-keys
    {:labels s/Str
     :kill_chain_phases.kill_chain_name s/Str
     :kill_chain_phases.phase_name s/Str
     :tool_version s/Str
     :sort_by tool-sort-fields})))

(s/defschema ToolGetParams ToolFieldsParam)

(s/defschema ToolByExternalIdQueryParams
  (st/merge PagingParams
            ToolFieldsParam))

(def capabilities
  #{:create-tool
    :read-tool
    :delete-tool
    :search-tool})

(def tool-routes
  (entity-crud-routes
   {:entity :tool
    :new-schema ts/NewTool
    :entity-schema ts/Tool
    :get-schema ts/PartialTool
    :get-params ToolGetParams
    :list-schema ts/PartialToolList
    :search-schema ts/PartialToolList
    :external-id-q-params ToolByExternalIdQueryParams
    :search-q-params ToolSearchParams
    :new-spec :new-tool/map
    :realize-fn ts/realize-tool
    :get-capabilities :read-tool
    :post-capabilities :create-tool
    :put-capabilities :create-tool
    :delete-capabilities :delete-tool
    :search-capabilities :search-tool
    :external-id-capabilities #{:read-tool :external-id}}))

(def tool-entity
  {:route-context "/tool"
   :tags ["Tool"]
   :entity :tool
   :plural :tools
   :schema ts/Tool
   :partial-schema ts/PartialTool
   :partial-list-schema ts/PartialToolList
   :new-schema ts/NewTool
   :stored-schema ts/StoredTool
   :partial-stored-schema ts/PartialStoredTool
   :realize-fn ts/realize-tool
   :es-store ->ToolStore
   :es-mapping tool-mapping
   :routes tool-routes
   :capabilities capabilities})
