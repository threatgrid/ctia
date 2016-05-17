(ns ctia.test-helpers.generators.schemas.coa-generators
  (:require [clojure.test.check.generators :as gen]
            [ctia.lib.time :as time]
            [ctia.schemas
             [coa :refer [NewCOA StoredCOA]]
             [common :as schemas-common]]
            [ctia.test-helpers.generators.common
             :refer [complete leaf-generators maybe]
             :as common]
            [ctia.test-helpers.generators.id :as gen-id]))

(def gen-coa
  (gen/fmap
   (fn [id]
     (complete
      StoredCOA
      {:id id}))
   (gen-id/gen-short-id-of-type :coa)))

(def gen-new-coa
  (gen/fmap
   (fn [[id
         [start-time end-time]]]
     (complete
      NewCOA
      (cond-> {}
        id
        (assoc :id id)

        start-time
        (assoc-in [:valid_time :start_time] start-time)

        end-time
        (assoc-in [:valid_time :end_time] end-time))))
   (gen/tuple
    (maybe (gen-id/gen-short-id-of-type :coa))
    ;; complete doesn't seem to generate :valid_time values, so do it manually
    common/gen-valid-time-tuple)))
