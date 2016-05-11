(ns ctia.stores.es.campaign
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.campaign :refer [StoredCampaign]]))

(def handle-create-campaign (crud/handle-create :campaign StoredCampaign))
(def handle-read-campaign (crud/handle-read :campaign StoredCampaign))
(def handle-update-campaign (crud/handle-update :campaign StoredCampaign))
(def handle-delete-campaign (crud/handle-delete :campaign StoredCampaign))
(def handle-list-campaigns (crud/handle-find :campaign StoredCampaign))

(defn handle-list-campaigns-by-indicators
  [state indicators params]
  (let [campaign-ids (some->> (map :related_campaigns indicators)
                              (mapcat #(map :campaign_id %))
                              vec)]
    (handle-list-campaigns state
                           {:id campaign-ids}
                           params)))
