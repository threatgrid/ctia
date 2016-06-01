(ns ctia.stores.es.sighting
  (:require
   [schema.core :as s]
   [ctia.stores.es.crud :as crud]
   [ctia.stores.es.query :refer [sightings-by-observables-query]]
   [ctim.schemas.sighting :refer [Sighting
                                  NewSighting
                                  StoredSighting]]
   [ctim.schemas.indicator :refer [Indicator]]
   [ctia.lib.es.document :refer [search-docs]]))


(def handle-create-sighting (crud/handle-create :sighting StoredSighting))
(def handle-read-sighting (crud/handle-read :sighting StoredSighting))
(def handle-update-sighting (crud/handle-update :sighting StoredSighting))
(def handle-delete-sighting (crud/handle-delete :sighting StoredSighting))
(def handle-list-sightings (crud/handle-find :sighting StoredSighting))

(def ^{:private true} mapping "sighting")

(defn handle-list-sightings-by-observables
  [{:keys [conn index]}  observables params]

  (search-docs conn
               index
               mapping
               nil
               (assoc params :query (sightings-by-observables-query observables))))
