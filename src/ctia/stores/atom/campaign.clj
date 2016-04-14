(ns ctia.stores.atom.campaign
  (:require [ctia.schemas.campaign :refer [StoredCampaign]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-campaign (mc/create-handler-from-realized StoredCampaign))
(def handle-read-campaign (mc/read-handler StoredCampaign))
(def handle-update-campaign (mc/update-handler-from-realized StoredCampaign))
(def handle-delete-campaign (mc/delete-handler StoredCampaign))

