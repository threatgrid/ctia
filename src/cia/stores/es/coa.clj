(ns cia.stores.es.coa
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [cia.stores.es.index :refer [es-conn index-name]]
   [cia.schemas.coa :refer [COA
                            NewCOA
                            realize-coa]]
   [cia.stores.es.document :refer [create-doc
                                   update-doc
                                   get-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "coa")

(defn- make-id [schema j]
  (str "coa" "-" (UUID/randomUUID)))

(defn handle-create-coa [state new-coa]
  (let [id (make-id COA new-coa)
        realized (realize-coa new-coa id)]
    (create-doc es-conn index-name mapping realized)))

(defn handle-update-coa [state id new-coa]
  (update-doc
   es-conn
   index-name
   mapping
   id
   new-coa))

(defn handle-read-coa [state id]
  (get-doc es-conn index-name mapping id))

(defn handle-delete-coa [state id]
  (delete-doc es-conn index-name mapping id))

(defn handle-list-coas [state filter-map]
  (search-docs es-conn index-name mapping filter-map))
