(ns ctia.attack-pattern.store.es
  (:require
   [ctia.store :refer [IStore IQueryStringSearchableStore]]
   [ctia.stores.es.crud :as crud]
   [ctia.schemas.core :refer [StoredAttackPattern PartialStoredAttackPattern]]))

(def handle-create (crud/handle-create :attack-pattern StoredAttackPattern))
(def handle-read (crud/handle-read :attack-pattern PartialStoredAttackPattern))
(def handle-update (crud/handle-update :attack-pattern StoredAttackPattern))
(def handle-delete (crud/handle-delete :attack-pattern StoredAttackPattern))
(def handle-list (crud/handle-find :attack-pattern PartialStoredAttackPattern))
(def handle-query-string-search (crud/handle-query-string-search :attack-pattern PartialStoredAttackPattern))

(defrecord AttackPatternStore [state]
  IStore
  (read [_ id ident params]
    (handle-read state id ident params))
  (create [_ new-attack-patterns ident params]
    (handle-create state new-attack-patterns ident params))
  (update [_ id attack-pattern ident]
    (handle-update state id attack-pattern ident))
  (delete [_ id ident]
    (handle-delete state id ident))
  (list [_ filter-map ident params]
    (handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search state query filtermap ident params)))
