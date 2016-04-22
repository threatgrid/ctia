(ns ctia.stores.es.sighting
  (:require
   [schema.core :as s]
   [ctia.stores.es.crud :as crud]
   [ctia.schemas.sighting :refer [Sighting
                                  NewSighting
                                  StoredSighting
                                  realize-sighting]]
   [ctia.schemas.indicator :refer [Indicator]]
   [ctia.lib.es.document :refer [search-docs]]))


(def handle-create-sighting (crud/handle-create :sighting StoredSighting))
(def handle-read-sighting (crud/handle-read :sighting StoredSighting))
(def handle-update-sighting (crud/handle-update :sighting StoredSighting))
(def handle-delete-sighting (crud/handle-delete :sighting StoredSighting))
(def handle-list-sightings (crud/handle-find :sighting StoredSighting))

(def ^{:private true} mapping "sighting")

(defn handle-list-sightings-by-indicators
  [state indicators]
  
  (let [indicator-ids (mapv :id indicators)]
    (handle-list-sightings state {:type "sighting"
                                  [:indicator :indicator_id] indicator-ids})))
