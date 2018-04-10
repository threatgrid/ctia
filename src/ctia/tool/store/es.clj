(ns ctia.tool.store.es
  (:require
   [ctia.store :refer [IStore
                       IQueryStringSearchableStore]]
   [ctia.stores.es.crud :as crud]
   [ctia.schemas.core :refer [StoredTool PartialStoredTool]]))

(def handle-create (crud/handle-create :tool StoredTool))
(def handle-read (crud/handle-read :tool PartialStoredTool))
(def handle-update (crud/handle-update :tool StoredTool))
(def handle-delete (crud/handle-delete :tool StoredTool))
(def handle-list (crud/handle-find :tool PartialStoredTool))
(def handle-query-string-search (crud/handle-query-string-search :tool PartialStoredTool))

(defrecord ToolStore [state]
  IStore
  (read [_ id ident params]
    (handle-read state id ident params))
  (create [_ new-tools ident params]
    (handle-create state new-tools ident params))
  (update [_ id tool ident]
    (handle-update state id tool ident))
  (delete [_ id ident]
    (handle-delete state id ident))
  (list [_ filter-map ident params]
    (handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search state query filtermap ident params)))
