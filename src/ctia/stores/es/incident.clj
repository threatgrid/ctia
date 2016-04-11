(ns ctia.stores.es.incident
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [schema.coerce :as c]
   [ring.swagger.coerce :as sc]
   [ctia.schemas.incident :refer [Incident
                                  NewIncident
                                  StoredIncident
                                  realize-incident]]
   [ctia.stores.es.document :refer [create-doc
                                    update-doc
                                    get-doc
                                    delete-doc
                                    search-docs]]))

(def ^{:private true} mapping "incident")

(def coerce-stored-incident
  (c/coercer! (s/maybe StoredIncident)
              sc/json-schema-coercion-matcher))

(defn handle-create-incident [state login realized-new-incident]
  (-> (create-doc (:conn state)
                  (:index state)
                  mapping
                  realized-new-incident)
      coerce-stored-incident))

(defn handle-update-incident [state id login new-incident]
  (-> (update-doc (:conn state)
                  (:index state)
                  mapping
                  id
                  new-incident)
      coerce-stored-incident))

(defn handle-read-incident [state id]
  (-> (get-doc (:conn state)
               (:index state)
               mapping
               id)
      coerce-stored-incident))

(defn handle-delete-incident [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-incidents [state filter-map]
  (->> (search-docs (:conn state)
                    (:index state)
                    mapping
                    filter-map)
       (map coerce-stored-incident)))
