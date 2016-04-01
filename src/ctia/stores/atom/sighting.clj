(ns ctia.stores.atom.sighting
  (:require [ctia.schemas.indicator :refer [StoredIndicator]]
            [ctia.schemas.sighting
             :refer [NewSighting StoredSighting realize-sighting]]
            [ctia.store :refer [ISightingStore]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(def swap-sighting (mc/make-swap-fn realize-sighting))

(mc/def-create-handler handle-create-sighting
  StoredSighting NewSighting swap-sighting (mc/random-id "sighting"))

(mc/def-update-handler handle-update-sighting
  StoredSighting NewSighting swap-sighting)

(mc/def-read-handler handle-read-sighting StoredSighting)

(mc/def-delete-handler handle-delete-sighting StoredSighting)

(mc/def-list-handler handle-list-sightings StoredSighting)

(s/defn handle-list-sightings-by-indicators :- (s/maybe [StoredSighting])
  [sightings-state :- (s/atom {s/Str StoredSighting})
   indicators :- (s/maybe [StoredIndicator])]
  ;; Find sightings using the :sightings relationship on indicators
  (let [sightings-map @sightings-state]
    (->> indicators
         (map :sightings)
         flatten
         (map :sighting_id)
         (remove nil?)
         (map (fn [s-id]
                (get sightings-map s-id)))
         (remove nil?))))
