(ns ctia.test-helpers.generators.schemas.sighting-generators
  (:require [clojure.test.check.generators :as gen]
            [ctia.lib.time :as time]
            [ctia.schemas
             [common :as schemas-common]
             [sighting :refer [NewSighting StoredSighting]]]
            [ctia.test-helpers.generators.common
             :refer [complete leaf-generators maybe]
             :as common]
            [ctia.test-helpers.generators.id :as gen-id]))

(def gen-sighting
  (gen/fmap
   (fn [id]
     (complete
      StoredSighting
      {:id id}))
   (gen-id/gen-short-id-of-type :sighting)))

(def gen-new-sighting
  (gen/fmap
   (fn [id]
     (complete
      NewSighting
      (cond-> {}
        id (assoc :id id))))
   (maybe (gen-id/gen-short-id-of-type :sighting))))
