(ns ctia.stores.es.indicator
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [ctia.store :refer :all]
   [ctia.schemas.common :refer [Observable]]
   [ctia.schemas.indicator :refer [Indicator
                                  NewIndicator
                                  StoredIndicator
                                  realize-indicator]]
   [ctia.stores.es.query :refer [indicators-by-judgements-query]]
   [ctia.stores.es.document :refer [create-doc
                                   update-doc
                                   get-doc
                                   delete-doc
                                   search-docs
                                   raw-search-docs]]))

(def ^{:private true} mapping "indicator")

(defn- make-id [schema j]
  (str "indicator" "-" (UUID/randomUUID)))

(defn handle-create-indicator [state login new-indicator]
  (let [id (make-id Indicator new-indicator)
        realized (realize-indicator new-indicator id login)]
    (create-doc (:conn state)
                (:index state)
                mapping
                realized)))

(defn handle-update-indicator [state login id new-indicator]
  (update-doc (:conn state)
              (:index state)
              mapping
              id
              new-indicator))

(defn handle-read-indicator [state id]
  (get-doc (:conn state)
           (:index state)
           mapping
           id))

(defn handle-delete-indicator [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-indicators [state filter-map]
  (search-docs (:conn state)
               (:index state)
               mapping
               filter-map))

(defn handle-list-indicators-by-judgements
  [state judgements]
  (raw-search-docs  (:conn state)
                    (:index state)
                    mapping
                    (indicators-by-judgements-query (map :id judgements))
                    {:created "desc"}))
