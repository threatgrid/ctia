(ns ctia.stores.es.campaign
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.campaign :refer [StoredCampaign]]))

(def handle-create-campaign (crud/handle-create :campaign StoredCampaign))
(def handle-read-campaign (crud/handle-read :campaign StoredCampaign))
(def handle-update-campaign (crud/handle-update :campaign StoredCampaign))
(def handle-delete-campaign (crud/handle-delete :campaign StoredCampaign))
(def handle-list-campaigns (crud/handle-find :campaign StoredCampaign))
