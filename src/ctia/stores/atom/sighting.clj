(ns ctia.stores.atom.sighting
  (:require [ctia.lib.schema :as ls]
            [ctia.store :refer [ISightingStore]]
            [ctia.stores.atom.common :as mc]
            [ctia.lib.pagination :refer [list-response-schema]]
            [schema.core :as s]
            [ctia.schemas.core :refer [StoredIndicator
                                       NewSighting
                                       StoredSighting
                                       Observable]]))

(def handle-create-sighting (mc/create-handler-from-realized StoredSighting))
(def handle-read-sighting (mc/read-handler StoredSighting))
(def handle-update-sighting (mc/update-handler-from-realized StoredSighting))
(def handle-delete-sighting (mc/delete-handler StoredSighting))
(def handle-list-sightings (mc/list-handler StoredSighting))

(s/defn handle-list-sightings-by-observables :- (list-response-schema StoredSighting)
  [sightings-state :- (ls/atom {s/Str StoredSighting})
   observables :- (s/maybe [Observable])
   params]
  (handle-list-sightings sightings-state
                         {:observables (set observables)} params))
