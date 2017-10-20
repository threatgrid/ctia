(ns ctia.stores.es.tool
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredTool]]))

(def handle-create (crud/handle-create :tool StoredTool))
(def handle-read (crud/handle-read :tool StoredTool))
(def handle-update (crud/handle-update :tool StoredTool))
(def handle-delete (crud/handle-delete :tool StoredTool))
(def handle-list (crud/handle-find :tool StoredTool))
(def handle-query-string-search (crud/handle-query-string-search :tool StoredTool))
