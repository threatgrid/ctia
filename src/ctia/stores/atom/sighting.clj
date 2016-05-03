(ns ctia.stores.atom.sighting
  (:require
   [ctia.schemas
    [indicator :refer [StoredIndicator]]
    [sighting :refer [StoredSighting]]]
   [ctia.stores.atom.common :as mc]
   [ctia.lib.pagination :refer [list-response-schema]]
   [schema.core :as s]))

(def handle-create-sighting (mc/create-handler-from-realized StoredSighting))
(def handle-read-sighting (mc/read-handler StoredSighting))
(def handle-update-sighting (mc/update-handler-from-realized StoredSighting))
(def handle-delete-sighting (mc/delete-handler StoredSighting))
(def handle-list-sightings (mc/list-handler StoredSighting))

(s/defn handle-list-sightings-by-indicators :- (list-response-schema StoredSighting)
  [sightings-state :- (s/atom {s/Str StoredSighting})
   indicators :- (s/maybe [StoredIndicator])
   params]
  ;; Find sightings using the :sightings relationship on indicators
  (let [sighting-ids (->> indicators
                          (map :sightings)
                          flatten
                          (map :sighting_id)
                          set)]

    (handle-list-sightings sightings-state {:id sighting-ids} params)))
