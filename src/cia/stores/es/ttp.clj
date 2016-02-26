(ns cia.stores.es.ttp
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [cia.stores.es.index :refer [es-conn index-name]]
   [cia.schemas.ttp :refer [TTP
                            NewTTP
                            realize-ttp]]
   [cia.stores.es.document :refer [create-doc
                                   update-doc
                                   get-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "ttp")

(defn- make-id [schema j]
  (str "ttp" "-" (UUID/randomUUID)))

(defn handle-create-ttp [state new-ttp]
  (let [id (make-id TTP new-ttp)
        realized (realize-ttp new-ttp id)]
    (create-doc es-conn index-name mapping realized)))

(defn handle-update-ttp [state id new-ttp]
  (update-doc
   es-conn
   index-name
   mapping
   id
   new-ttp))

(defn handle-read-ttp [state id]
  (get-doc es-conn index-name mapping id))

(defn handle-delete-ttp [state id]
  (delete-doc es-conn index-name mapping id))

(defn handle-list-ttps [state filter-map]
  (search-docs es-conn index-name mapping filter-map))
