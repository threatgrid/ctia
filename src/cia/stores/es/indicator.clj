(ns cia.stores.es.indicator
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [cia.store :refer :all]
   [cia.stores.es.index :refer [es-conn index-name]]
   [cia.schemas.common :refer [Observable]]
   [cia.schemas.indicator :refer [Indicator
                                  NewIndicator
                                  StoredIndicator
                                  realize-indicator]]
   [cia.stores.es.document :refer [create-doc
                                   update-doc
                                   get-doc
                                   delete-doc
                                   search-docs
                                   raw-search-docs]]))

(def ^{:private true} mapping "indicator")

(defn- make-id [schema j]
  (str "indicator" "-" (UUID/randomUUID)))

(defn handle-create-indicator [state new-indicator]
  (let [id (make-id Indicator new-indicator)
        realized (realize-indicator new-indicator id)]
    (create-doc es-conn index-name mapping realized)))

(defn handle-update-indicator [state id new-indicator]
  (update-doc
   es-conn
   index-name
   mapping
   id
   new-indicator))

(defn handle-read-indicator [state id]
  (get-doc es-conn index-name mapping id))

(defn handle-delete-indicator [state id]
  (delete-doc es-conn index-name mapping id))

(defn handle-list-indicators [state  filter-map]
  (search-docs es-conn index-name mapping filter-map))

(defn handle-list-indicators-by-observable
  [indicator-state judgement-store observable]

  (let [judgements (list-judgements judgement-store
                                    {[:observable :type] (:type observable)
                                     [:observable :value] (:value observable)})
        judgements-ids (set (map :id judgements))
        query {:filtered
               {:filter
                {:nested
                 {:path "judgements"
                  :query
                  {:bool
                   {:must
                    {:terms
                     {:judgements.judgement judgements-ids}}}}}}}}]
    (raw-search-docs
     es-conn
     index-name
     mapping
     query
     {:timestamp "desc"})))
