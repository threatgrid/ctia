(ns ctia.stores.atom.campaign
  (:require [ctia.schemas.campaign
             :refer [NewCampaign StoredCampaign realize-campaign]]
            [ctia.stores.atom.common :as mc]))

(def swap-campaign (mc/make-swap-fn realize-campaign))

(mc/def-create-handler-from-realized handle-create-campaign StoredCampaign)

(mc/def-read-handler handle-read-campaign StoredCampaign)

(mc/def-delete-handler handle-delete-campaign StoredCampaign)

(mc/def-update-handler-from-realized handle-update-campaign StoredCampaign)
