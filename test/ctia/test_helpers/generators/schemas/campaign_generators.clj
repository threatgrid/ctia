(ns ctia.test-helpers.generators.schemas.campaign-generators
  (:require [clojure.test.check.generators :as gen]
            [ctia.lib.time :as time]
            [ctia.schemas
             [campaign :refer [NewCampaign StoredCampaign]]
             [common :as schemas-common]]
            [ctia.test-helpers.generators.common
             :refer [complete leaf-generators maybe]
             :as common]
            [ctia.test-helpers.generators.id :as gen-id]))

(def gen-campaign
  (gen/fmap
   (fn [id]
     (complete
      StoredCampaign
      {:id id}))
   (gen-id/gen-short-id-of-type :campaign)))

(def gen-new-campaign
  (gen/fmap
   (fn [[id
         [start-time end-time]]]
     (complete
      NewCampaign
      (cond-> {}
        id
        (assoc :id id)

        start-time
        (assoc-in [:valid_time :start_time] start-time)

        end-time
        (assoc-in [:valid_time :end_time] end-time))))
   (gen/tuple
    (maybe (gen-id/gen-short-id-of-type :campaign))
    ;; complete doesn't seem to generate :valid_time values, so do it manually
    common/gen-valid-time-tuple)))
