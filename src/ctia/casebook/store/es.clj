(ns ctia.casebook.store.es
  (:require
   [ctia.store :refer [IStore IQueryStringSearchableStore]]
   [ctia.schemas.core
    :refer [StoredCasebook
            PartialStoredCasebook]]
   [ctia.stores.es.crud :as crud]))

(def handle-create (crud/handle-create :casebook StoredCasebook))
(def handle-read (crud/handle-read :casebook PartialStoredCasebook))
(def handle-update (crud/handle-update :casebook StoredCasebook))
(def handle-delete (crud/handle-delete :casebook StoredCasebook))
(def handle-list (crud/handle-find :casebook PartialStoredCasebook))
(def handle-query-string-search (crud/handle-query-string-search :casebook PartialStoredCasebook))

(defrecord CasebookStore [state]
  IStore
  (read [_ id ident params]
    (handle-read state id ident params))
  (create [_ new-casebooks ident params]
    (handle-create state new-casebooks ident params))
  (update [_ id casebook ident]
    (handle-update state id casebook ident))
  (delete [this id ident]
    (handle-delete state id ident))
  (list [this filtermap ident params]
    (handle-list state filtermap ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search state query filtermap ident params)))
