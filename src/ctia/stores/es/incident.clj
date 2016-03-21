(ns ctia.stores.es.incident
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [ctia.schemas.incident :refer [Incident
                                 NewIncident
                                 realize-incident]]
   [ctia.stores.es.document :refer [create-doc
                                   update-doc
                                   get-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "incident")

(defn- make-id [schema j]
  (str "incident" "-" (UUID/randomUUID)))

(defn handle-create-incident [state login new-incident]
  (let [id (make-id Incident new-incident)
        realized (realize-incident new-incident id login)]
    (create-doc (:conn state)
                (:index state)
                mapping
                realized)))

(defn handle-update-incident [state login id new-incident]
  (update-doc (:conn state)
              (:index state)
              mapping
              id
              new-incident))

(defn handle-read-incident [state id]
  (get-doc (:conn state)
           (:index state)
           mapping
           id))

(defn handle-delete-incident [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-incidents [state filter-map]
  (search-docs (:conn state)
               (:index state)
               mapping
               filter-map))
