(ns ctia.test-helpers.generators.schemas.incident-generators
  (:require [clojure.test.check.generators :as gen]
            [ctia.lib.time :as time]
            [ctia.schemas
             [common :as schemas-common]
             [incident :refer [NewIncident StoredIncident]]]
            [ctia.test-helpers.generators.common
             :refer [complete leaf-generators maybe]
             :as common]
            [ctia.test-helpers.generators.id :as gen-id]))

(def gen-incident
  (gen/fmap
   (fn [id]
     (complete
      StoredIncident
      {:id id}))
   (gen-id/gen-short-id-of-type :incident)))

(def gen-new-incident
  (gen/fmap
   (fn [[id
         [start-time end-time]]]
     (complete
      NewIncident
      (cond-> {}
        id
        (assoc :id id)

        start-time
        (assoc-in [:valid_time :start_time] start-time)

        end-time
        (assoc-in [:valid_time :end_time] end-time))))
   (gen/tuple
    (maybe (gen-id/gen-short-id-of-type :incident))
    ;; complete doesn't seem to generate :valid_time values, so do it manually
    common/gen-valid-time-tuple)))
