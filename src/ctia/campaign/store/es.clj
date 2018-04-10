(ns ctia.campaign.store.es
  (:require
   [ctia.store :refer [IStore IQueryStringSearchableStore]]
   [ctia.stores.es.crud :as crud]
   [ctia.schemas.core :refer [StoredCampaign PartialStoredCampaign]]))

(def handle-create (crud/handle-create :campaign StoredCampaign))
(def handle-read (crud/handle-read :campaign PartialStoredCampaign))
(def handle-update (crud/handle-update :campaign StoredCampaign))
(def handle-delete (crud/handle-delete :campaign StoredCampaign))
(def handle-list (crud/handle-find :campaign PartialStoredCampaign))
(def handle-query-string-search (crud/handle-query-string-search :campaign PartialStoredCampaign))

(defrecord CampaignStore [state]
  IStore
  (read [_ id ident params]
    (handle-read state id ident params))
  (create [_ new-campaigns ident params]
    (handle-create state new-campaigns ident params))
  (update [_ id new-campaign ident]
    (handle-update state id new-campaign ident))
  (delete [_ id ident]
    (handle-delete state id ident))
  (list [_ filter-map ident params]
    (handle-list state filter-map ident params))
  IQueryStringSearchableStore
  (query-string-search [_ query filtermap ident params]
    (handle-query-string-search state query filtermap ident params)))
