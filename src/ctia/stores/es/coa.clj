(ns ctia.stores.es.coa
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [schema.coerce :as c]
   [ring.swagger.coerce :as sc]
   [ctia.schemas.coa :refer [COA
                             NewCOA
                             StoredCOA
                             realize-coa]]
   [ctia.stores.es.document :refer [create-doc
                                    update-doc
                                    get-doc
                                    delete-doc
                                    search-docs]]))

(def ^{:private true} mapping "coa")

(def coerce-stored-coa
  (c/coercer! (s/maybe StoredCOA)
              sc/json-schema-coercion-matcher))

(defn handle-create-coa [state login realized-new-coa]
  (-> (create-doc (:conn state)
                  (:index state)
                  mapping
                  realized-new-coa)
      coerce-stored-coa))

(defn handle-update-coa [state login id new-coa]
  (-> (update-doc (:conn state)
                  (:index state)
                  mapping
                  id
                  new-coa)
      coerce-stored-coa))

(defn handle-read-coa [state id]
  (-> (get-doc (:conn state)
               (:index state)
               mapping
               id)
      coerce-stored-coa))

(defn handle-delete-coa [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-coas [state filter-map]
  (->> (search-docs (:conn state)
                    (:index state)
                    mapping
                    filter-map)
       (map coerce-stored-coa)))
