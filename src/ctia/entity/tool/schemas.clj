(ns ctia.entity.tool.schemas
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.schemas.core :refer [def-acl-schema def-stored-schema]]
            [ctim.schemas.tool :as tool]
            [ctia.schemas.sorting :as sorting]
            [flanders.utils :as fu]
            [schema.core :as s]
            [schema-tools.core :as st]))

(def-acl-schema Tool
  tool/Tool
  "tool")

(def-acl-schema PartialTool
  (fu/optionalize-all tool/Tool)
  "partial-tool")

(s/defschema PartialToolList [PartialTool])

(def-acl-schema NewTool
  tool/NewTool
  "new-tool")

(def-stored-schema StoredTool Tool)

(s/defschema PartialStoredTool
  (st/optional-keys-schema StoredTool))

(def realize-tool
  (default-realize-fn "tool" NewTool StoredTool))

(def tool-fields sorting/default-entity-sort-fields)
