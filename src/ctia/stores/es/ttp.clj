(ns ctia.stores.es.ttp
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [schema.coerce :as c]
   [ring.swagger.coerce :as sc]
   [ctia.schemas.ttp :refer [TTP
                             NewTTP
                             StoredTTP
                             realize-ttp]]
   [ctia.stores.es.document :refer [create-doc
                                    update-doc
                                    get-doc
                                    delete-doc
                                    search-docs]]))

(def ^{:private true} mapping "ttp")

(def coerce-stored-ttp
  (c/coercer! (s/maybe StoredTTP)
              sc/json-schema-coercion-matcher))

(defn handle-create-ttp [state login realized-new-ttp]
  (-> (create-doc (:conn state)
                  (:index state)
                  mapping
                  realized-new-ttp)
      coerce-stored-ttp))

(defn handle-update-ttp [state id login new-ttp]
  (-> (update-doc (:conn state)
                  (:index state)
                  mapping
                  id
                  new-ttp)
      coerce-stored-ttp))

(defn handle-read-ttp [state id]
  (-> (get-doc (:conn state)
               (:index state)
               mapping
               id)
      coerce-stored-ttp))

(defn handle-delete-ttp [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-ttps [state filter-map]
  (->> (search-docs (:conn state)
                    (:index state)
                    mapping
                    filter-map)
       (map coerce-stored-ttp)))
