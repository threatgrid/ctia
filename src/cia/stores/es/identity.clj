(ns cia.stores.es.identity
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [cia.schemas.identity :refer [Identity]]
   [cia.stores.es.document :refer [create-doc
                                   get-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "identity")

(defn handle-create-identity [state new-identity]
  (let [id (:login new-identity)
        realized (assoc new-identity :id id)]
    (create-doc (:conn state)
                (:index state)
                mapping
                realized)))

(defn handle-read-identity [state login]
  (get-doc (:conn state)
           (:index state)
           mapping
           login))

(defn handle-delete-identity [state login]
  (delete-doc (:conn state)
              (:index state)
              mapping
              login))
