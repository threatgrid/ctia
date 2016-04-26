(ns ctia.stores.atom.sighting
  (:require [ctia.schemas.indicator :refer [StoredIndicator]]
            [ctia.schemas.sighting
             :refer [NewSighting StoredSighting realize-sighting]]
            [ctia.store :refer [ISightingStore]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(def handle-create-sighting (mc/create-handler-from-realized StoredSighting))
(def handle-read-sighting (mc/read-handler StoredSighting))
(def handle-update-sighting (mc/update-handler-from-realized StoredSighting))
(def handle-delete-sighting (mc/delete-handler StoredSighting))
(def handle-list-sightings (mc/list-handler StoredSighting))

(s/defn handle-list-sightings-by-indicators :- (s/maybe [StoredSighting])
  [sightings-state :- (s/atom {s/Str StoredSighting})
   indicators :- (s/maybe [StoredIndicator])]
  ;; Find sightings using the :sightings relationship on indicators
  (let [sightings-map @sightings-state
        indicators-set (set (map #({:indicator_id (:id %)}) indicators))]
    (handle-list-sightings sightings-state
                           {:indicators indicators-set})))
