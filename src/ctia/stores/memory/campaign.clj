(ns ctia.stores.memory.campaign
  (:require [ctia.schemas.campaign
             :refer [NewCampaign StoredCampaign realize-campaign]]
            [ctia.store :refer [ICampaignStore]]
            [ctia.stores.memory.common :as mc]))

(def swap-campaign (mc/make-swap-fn realize-campaign))

(mc/def-create-handler handle-create-campaign
  StoredCampaign NewCampaign swap-campaign (mc/random-id "campaign"))

(mc/def-read-handler handle-read-campaign StoredCampaign)

(mc/def-delete-handler handle-delete-campaign StoredCampaign)

(mc/def-update-handler handle-update-campaign
  StoredCampaign NewCampaign swap-campaign)

(defrecord CampaignStore [state]
  ICampaignStore
  (read-campaign [_ id]
    (handle-read-campaign state id))
  (create-campaign [_ login new-campaign]
    (handle-create-campaign state login new-campaign))
  (update-campaign [_ id login new-campaign]
    (handle-update-campaign state id login new-campaign))
  (delete-campaign [_ id]
    (handle-delete-campaign state id))
  (list-campaigns [_ filter-map]))
