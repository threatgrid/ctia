(ns cia.stores.es.actor
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
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
    (create-doc (:conn state)
                (:index state)
                mapping
                realized)))

(defn handle-update-actor [state id new-actor]
  (update-doc (:conn state)
              (:index state)
              mapping
              id
              new-actor))

(defn handle-read-actor [state id]
  (get-doc (:conn state)
           (:index state)
           mapping
           id))

(defn handle-delete-actor [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-actors [state judgement-store filter-map]
  (search-docs (:conn state)
               (:index state)
               mapping
               filter-map))
