(ns ctia.incident.store.es
  (:require
   [ctia.store :refer [IStore IQueryStringSearchableStore]]
   [ctia.stores.es.crud :as crud]
   [ctia.schemas.core
    :refer [StoredIncident PartialStoredIncident]]))

(def handle-create (crud/handle-create :incident StoredIncident))
(def handle-read (crud/handle-read :incident PartialStoredIncident))
(def handle-update (crud/handle-update :incident StoredIncident))
(def handle-delete (crud/handle-delete :incident StoredIncident))
(def handle-list (crud/handle-find :incident PartialStoredIncident))
(def handle-query-string-search (crud/handle-query-string-search :incident PartialStoredIncident))

(defrecord IncidentStore [state]
  IStore
  (read [_ id ident params]
    (handle-read state id ident params))
  (create [_ new-incidents ident params]
    (handle-create state new-incidents ident params))
  (update [_ id new-incident ident]
    (handle-update state id new-incident ident))
  (delete [_ id ident]
    (handle-delete state id ident))
  (list [_ filter-map ident params]
    (handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search state query filtermap ident params)))
