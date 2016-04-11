(ns ctia.stores.es.ttp
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [ctia.schemas.ttp :refer [TTP
                            NewTTP
                            realize-ttp]]
   [ctia.stores.es.document :refer [create-doc
                                   update-doc
                                   get-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "ttp")

(defn handle-create-ttp [state login realized-new-ttp]
  (create-doc (:conn state)
                 (:index state)
                 mapping
                 realized-new-ttp))

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
