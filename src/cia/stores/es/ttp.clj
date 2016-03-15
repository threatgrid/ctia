(ns cia.stores.es.ttp
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
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

(defn handle-create-ttp [state login new-ttp]
  (let [id (make-id TTP new-ttp)
        realized (realize-ttp new-ttp id login)]
    (create-doc (:conn state)
                (:index state)
                mapping
                realized)))

(defn handle-update-ttp [state login id new-ttp]
  (update-doc (:conn state)
              (:index state)
              mapping
              id
              new-ttp))

(defn handle-read-ttp [state id]
  (get-doc (:conn state)
           (:index state)
           mapping
           id))

(defn handle-delete-ttp [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-ttps [state filter-map]
  (search-docs (:conn state)
               (:index state)
               mapping
               filter-map))
