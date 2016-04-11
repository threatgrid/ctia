(ns ctia.stores.es.actor
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [schema.coerce :as c]
   [ring.swagger.coerce :as sc]
   [ctia.schemas.actor :refer [Actor
                               NewActor
                               realize-actor
                               StoredActor]]
   [ctia.stores.es.document :refer [create-doc
                                    update-doc
                                    get-doc
                                    delete-doc
                                    search-docs]]))

(def ^{:private true} mapping "actor")

(def coerce-stored-actor
  (c/coercer! (s/maybe StoredActor)
              sc/json-schema-coercion-matcher))

(defn handle-create-actor [state _ realized-actor]
  (-> (create-doc (:conn state)
                  (:index state)
                  mapping
                  realized-actor)
      coerce-stored-actor))

(defn handle-update-actor [state id login realized-actor]
  (-> (update-doc (:conn state)
                  (:index state)
                  mapping
                  id
                  realized-actor)
      coerce-stored-actor))

(defn handle-read-actor [state id]
  (-> (get-doc (:conn state)
               (:index state)
               mapping
               id)
      coerce-stored-actor))

(defn handle-delete-actor [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-actors [state judgement-store filter-map]
  (-> (search-docs (:conn state)
                   (:index state)
                   mapping
                   filter-map)
      (map coerce-stored-actor)))
