(ns ctia.stores.es.campaign
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredCampaign PartialStoredCampaign]]))

(def handle-create (crud/handle-create :campaign StoredCampaign))
(def handle-read (crud/handle-read :campaign PartialStoredCampaign))
(def handle-update (crud/handle-update :campaign StoredCampaign))
(def handle-delete (crud/handle-delete :campaign StoredCampaign))
(def handle-list (crud/handle-find :campaign PartialStoredCampaign))
(def handle-query-string-search (crud/handle-query-string-search :campaign PartialStoredCampaign))
