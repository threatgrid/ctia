(ns ctia.relationship.store.es
  (:require
   [ctia.store :refer [IStore
                       IQueryStringSearchableStore]]
   [ctia.stores.es.crud :as crud]
   [ctia.schemas.core
    :refer [StoredRelationship
            PartialStoredRelationship]]))

(def handle-create (crud/handle-create :relationship StoredRelationship))
(def handle-read (crud/handle-read :relationship PartialStoredRelationship))
(def handle-update (crud/handle-update :relationship StoredRelationship))
(def handle-delete (crud/handle-delete :relationship StoredRelationship))
(def handle-list (crud/handle-find :relationship PartialStoredRelationship))
(def handle-query-string-search (crud/handle-query-string-search :relationship PartialStoredRelationship))

(defrecord RelationshipStore [state]
  IStore
  (create [_ new-relationships ident params]
    (handle-create state new-relationships ident params))
  (update [_ id new-relationship ident]
    (handle-update state id new-relationship ident))
  (read [_ id ident params]
    (handle-read state id ident params))
  (delete [_ id ident]
    (handle-delete state id ident))
  (list [_ filter-map ident params]
    (handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search state query filtermap ident params)))
