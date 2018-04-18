(ns ctia.entity.tool.schemas
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.schemas.core :refer [def-acl-schema def-stored-schema]]
            [ctim.schemas.tool :as tool]
            [ctia.schemas.sorting :as sorting]
            [flanders.utils :as fu]
            [schema.core :as s]))

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

(def-stored-schema StoredTool
  tool/StoredTool
  "stored-tool")

(def-stored-schema PartialStoredTool
  (fu/optionalize-all tool/StoredTool)
  "partial-stored-tool")

(def realize-tool
  (default-realize-fn "tool" NewTool StoredTool))

(def tool-fields
  (concat sorting/base-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:name]))
