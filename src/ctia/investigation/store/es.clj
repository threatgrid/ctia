(ns ctia.investigation.store.es
  (:require
   [ctia.store :refer [IStore IQueryStringSearchableStore]]
   [ctia.stores.es.crud :as crud]
   [ctia.schemas.core
    :refer [StoredInvestigation PartialStoredInvestigation]]))

(def handle-create (crud/handle-create :investigation StoredInvestigation))
(def handle-read (crud/handle-read :investigation PartialStoredInvestigation))
(def handle-update (crud/handle-update :investigation StoredInvestigation))
(def handle-delete (crud/handle-delete :investigation StoredInvestigation))
(def handle-list (crud/handle-find :investigation PartialStoredInvestigation))
(def handle-query-string-search (crud/handle-query-string-search
                                 :investigation PartialStoredInvestigation))

(defrecord InvestigationStore [state]
  IStore
  (read [_ id ident params]
    (handle-read state id ident params))
  (create [_ new-investigations ident params]
    (handle-create state new-investigations ident params))
  (update [_ id investigation ident]
    (handle-update state id investigation ident))
  (delete [this id ident]
    (handle-delete state id ident))
  (list [this filtermap ident params]
    (handle-list state filtermap ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search state query filtermap ident params)))
