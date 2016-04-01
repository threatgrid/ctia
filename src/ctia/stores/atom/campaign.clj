(ns ctia.stores.atom.campaign
  (:require [ctia.schemas.campaign
             :refer [NewCampaign StoredCampaign realize-campaign]]
            [ctia.stores.atom.common :as mc]))

(def swap-campaign (mc/make-swap-fn realize-campaign))

(mc/def-create-handler handle-create-campaign
  StoredCampaign NewCampaign swap-campaign (mc/random-id "campaign"))

(mc/def-read-handler handle-read-campaign StoredCampaign)

(mc/def-delete-handler handle-delete-campaign StoredCampaign)

(mc/def-update-handler handle-update-campaign
  StoredCampaign NewCampaign swap-campaign)
