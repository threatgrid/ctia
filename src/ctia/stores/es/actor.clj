(ns ctia.stores.es.actor
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [ctia.schemas.actor :refer [Actor
                               NewActor
                               realize-actor]]
   [ctia.stores.es.document :refer [create-doc
                                    update-doc
                                    get-doc
                                    delete-doc
                                    search-docs]]))

(def ^{:private true} mapping "actor")

(defn handle-create-actor [state _ realized-actor]
  (create-doc (:conn state)
              (:index state)
              mapping
              realized-actor))

(defn handle-update-actor [state login id realized-actor]
  (update-doc (:conn state)
              (:index state)
              mapping
              id
              realized-actor))

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
