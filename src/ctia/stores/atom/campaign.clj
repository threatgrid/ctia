(ns ctia.stores.atom.campaign
  (:require [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.schemas
             [campaign :refer [StoredCampaign]]
             [indicator :refer [StoredIndicator]]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(def handle-create-campaign (mc/create-handler-from-realized StoredCampaign))
(def handle-read-campaign (mc/read-handler StoredCampaign))
(def handle-update-campaign (mc/update-handler-from-realized StoredCampaign))
(def handle-delete-campaign (mc/delete-handler StoredCampaign))
(def handle-list-campaigns (mc/list-handler StoredCampaign))

(s/defn handle-list-campaigns-by-indicators :- (list-response-schema StoredCampaign)
  [campaign-state :- (s/atom {s/Str StoredCampaign})
   indicators :- [StoredIndicator]
   params]
  (let [campaign-ids (some->> (map :related_campaigns indicators)
                              (mapcat #(map :campaign_id %))
                              set)]
    (handle-list-campaigns campaign-state {:id campaign-ids} params)))
