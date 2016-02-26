(ns cia.stores.es.actor
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [cia.stores.es.index :refer [es-conn index-name]]
   [cia.schemas.actor :refer [Actor
                              NewActor
                              realize-actor]]
   [cia.stores.es.document :refer [create-doc
                                   update-doc
                                   get-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "actor")

(defn- make-id [schema j]
  (str "actor" "-" (UUID/randomUUID)))

(defn handle-create-actor [state new-actor]
  (let [id (make-id Actor new-actor)
        realized (realize-actor new-actor id)]
    (create-doc es-conn index-name mapping realized)))

(defn handle-update-actor [state id new-actor]
  (update-doc
   es-conn
   index-name
   mapping
   id
   new-actor))

(defn handle-read-actor [state id]
  (get-doc es-conn index-name mapping id))

(defn handle-delete-actor [state id]
  (delete-doc es-conn index-name mapping id))

(defn handle-list-actors [state judgement-store filter-map]
  (search-docs es-conn index-name mapping filter-map))
