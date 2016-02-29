(ns cia.stores.es.incident
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [cia.stores.es.index :refer [es-conn index-name]]
   [cia.schemas.incident :refer [Incident
                                 NewIncident
                                 realize-incident]]
   [cia.stores.es.document :refer [create-doc
                                   update-doc
                                   get-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "incident")

(defn- make-id [schema j]
  (str "incident" "-" (UUID/randomUUID)))

(defn handle-create-incident [state new-incident]
  (let [id (make-id Incident new-incident)
        realized (realize-incident new-incident id)]
    (create-doc es-conn index-name mapping realized)))

(defn handle-update-incident [state id new-incident]
  (update-doc
   es-conn
   index-name
   mapping
   id
   new-incident))

(defn handle-read-incident [state id]
  (get-doc es-conn index-name mapping id))

(defn handle-delete-incident [state id]
  (delete-doc es-conn index-name mapping id))

(defn handle-list-incidents [state filter-map]
  (search-docs es-conn index-name mapping filter-map))
