(ns ctia.campaign.store.es
  (:require [ctia.schemas.core
             :refer
             [PartialStoredCampaign StoredCampaign]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store CampaignStore :campaign StoredCampaign PartialStoredCampaign)
