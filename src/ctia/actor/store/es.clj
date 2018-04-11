(ns ctia.actor.store.es
  (:require [ctia.schemas.core :refer [PartialStoredActor StoredActor]]
            [ctia.store :refer [IQueryStringSearchableStore IStore]]
            [ctia.stores.es.crud :as crud]))

(def handle-create (crud/handle-create :actor StoredActor))
(def handle-read (crud/handle-read :actor PartialStoredActor))
(def handle-update (crud/handle-update :actor StoredActor))
(def handle-delete (crud/handle-delete :actor StoredActor))
(def handle-list (crud/handle-find :actor PartialStoredActor))
(def handle-query-string-search (crud/handle-query-string-search :actor PartialStoredActor))

(defrecord ActorStore [state]
  IStore
  (read [_ id ident params]
    (handle-read state id ident params))
  (create [_ new-actors ident params]
    (handle-create state new-actors ident params))
  (update [_ id actor ident]
    (handle-update state id actor ident))
  (delete [_ id ident]
    (handle-delete state id ident))
  (list [_ filter-map ident params]
    (handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search state query filtermap ident params)))
