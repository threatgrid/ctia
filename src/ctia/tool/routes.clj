(ns ctia.tool.routes
  (:require [ctia.domain.entities :refer [realize-tool]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [NewTool PartialTool PartialToolList Tool]]
             [sorting :as sorting]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def tool-sort-fields
  (apply s/enum sorting/tool-sort-fields))

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

(def tool-routes
  (entity-crud-routes
   {:entity :tool
    :new-schema NewTool
    :entity-schema Tool
    :get-schema PartialTool
    :get-params ToolGetParams
    :list-schema PartialToolList
    :search-schema PartialToolList
    :external-id-q-params ToolByExternalIdQueryParams
    :search-q-params ToolSearchParams
    :new-spec :new-tool/map
    :realize-fn realize-tool
    :get-capabilities :read-tool
    :post-capabilities :create-tool
    :put-capabilities :create-tool
    :delete-capabilities :delete-tool
    :search-capabilities :search-tool
    :external-id-capabilities #{:read-tool :external-id}}))
