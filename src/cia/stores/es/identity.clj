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

(defn capabilities->capabilities-set [caps]
  "transform a vec of capabilities from es
   to a set of keywords"
  (set (map #(-> % keyword) caps)))

(defn capabilities-set->capabilities [caps]
  "transform a set of capabilities
   into a vec of strings"
  (vec (map name caps)))

(defn handle-create-identity [state new-identity]
  (let [id (:login new-identity)
        realized (assoc new-identity :id id)
        transformed (update-in realized [:capabilities]
                               capabilities-set->capabilities)

        res (create-doc (:conn state)
                        (:index state)
                        mapping
                        transformed)]
    (update-in res [:capabilities] capabilities->capabilities-set)))

(defn handle-read-identity [state login]
  (-> (get-doc (:conn state)
               (:index state)
               mapping
               login)
      (update-in [:capabilities] capabilities->capabilities-set)))

(defn handle-delete-identity [state login]
  (delete-doc (:conn state)
              (:index state)
              mapping
              login))
