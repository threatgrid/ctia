(ns ctia.stores.atom.sighting
  (:require [ctia.schemas.indicator :refer [StoredIndicator]]
            [ctia.schemas.sighting
             :refer [NewSighting StoredSighting realize-sighting]]
            [ctia.store :refer [ISightingStore]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]
            [ctia.schemas.common :as c]))

(def handle-create-sighting (mc/create-handler-from-realized StoredSighting))
(def handle-read-sighting (mc/read-handler StoredSighting))
(def handle-update-sighting (mc/update-handler-from-realized StoredSighting))
(def handle-delete-sighting (mc/delete-handler StoredSighting))
(def handle-list-sightings (mc/list-handler StoredSighting))

(s/defn handle-list-sightings-by-indicators :- (s/maybe [StoredSighting])
  [sightings-state :- (s/atom {s/Str StoredSighting})
   indicators :- (s/maybe [StoredIndicator])]
  (let [indicators-set (set (map (fn [ind] {:indicator_id (:id ind)})
                                 indicators))]
    (handle-list-sightings sightings-state
                           {:indicators indicators-set})))

(s/defn handle-list-sightings-by-observables :- (s/maybe [StoredSighting])
  [sightings-state :- (s/atom {s/Str StoredSighting})
   observables :- (s/maybe [c/Observable])]
  (handle-list-sightings sightings-state {:observables (set observables)}))
