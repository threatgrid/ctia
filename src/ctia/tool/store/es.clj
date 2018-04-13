(ns ctia.tool.store.es
  (:require [ctia.schemas.core :refer [PartialStoredTool StoredTool]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store ToolStore :tool StoredTool PartialStoredTool)
