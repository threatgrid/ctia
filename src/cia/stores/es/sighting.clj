(ns cia.stores.es.sighting
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [clj-time.core :as t]
   [cia.schemas.sighting :refer [Sighting
                                 NewSighting
                                 realize-sighting]]
   [cia.schemas.indicator :refer [Indicator]]
   [cia.stores.es.document :refer [create-doc
                                   update-doc
                                   get-doc
                                   delete-doc
                                   search-docs
                                   raw-search-docs]]))

(def ^{:private true} mapping "sighting")

(defn- make-id [schema j]
  (str "sighting" "-" (UUID/randomUUID)))

(defn handle-create-sighting [state login new-sighting]
  (let [id (make-id Sighting new-sighting)
        realized (realize-sighting new-sighting id login)]

    (create-doc (:conn state)
                (:index state)
                mapping
                realized)))

(defn handle-update-sighting [state id login new-sighting]
  (update-doc (:conn state)
              (:index state)
              mapping
              id
              new-sighting))

(defn handle-read-sighting [state id]
  (get-doc (:conn state)
           (:index state)
           mapping
           id))

(defn handle-delete-sighting [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-sightings [state filter-map]
  (search-docs (:conn state)
               (:index state)
               mapping
               filter-map))

(defn handle-list-sightings-by-indicators
  [state indicators]

  (let [sighting-ids (->> indicators
                          (mapcat :sightings)
                          (map :sighting_id))]
    (raw-search-docs  (:conn state)
                      (:index state)
                      mapping
                      {:terms {:id sighting-ids}}
                      {:created "desc"})))
