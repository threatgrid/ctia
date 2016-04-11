(ns ctia.stores.atom.campaign
  (:require [ctia.schemas.campaign
             :refer [NewCampaign StoredCampaign realize-campaign]]
            [ctia.stores.atom.common :as mc]))

(def  handle-create-campaign (mc/create-handler-from-realized StoredCampaign))

(mc/def-read-handler handle-read-campaign StoredCampaign)

(mc/def-delete-handler handle-delete-campaign StoredCampaign)

(def handle-update-campaign (mc/update-handler-from-realized StoredCampaign))
