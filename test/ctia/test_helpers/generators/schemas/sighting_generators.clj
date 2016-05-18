(ns ctia.test-helpers.generators.schemas.sighting-generators
  (:require [clojure.test.check.generators :as gen]
            [ctia.lib.time :as time]
            [ctia.schemas
             [common :as schemas-common]
             [sighting :refer [NewSighting StoredSighting]]]
            [ctia.test-helpers.generators.common
             :refer [leaf-generators maybe]
             :as common]
            [ctia.test-helpers.generators.id :as gen-id]))

(def gen-short-id
  (gen-id/gen-short-id-of-type :sighting))

(defn complete [m]
  (common/complete
   StoredSighting
   m))

(def gen-sighting
  (gen/fmap
   (fn [id]
     (complete
      {:id id}))
   gen-short-id))

(defn gen-sighting-with-observables [observables]
  (gen/fmap
   (fn [id]
     (complete
      {:id id
       :observables observables}))
   gen-short-id))

(def gen-new-sighting
  (gen/fmap
   (fn [id]
     (common/complete
      NewSighting
      (cond-> {}
        id (assoc :id id))))
   (maybe gen-short-id)))
