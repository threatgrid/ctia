(ns cia.stores.es.indicator
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [cia.store :refer :all]
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
    (create-doc (:conn state)
                (:index state)
                mapping
                realized)))

(defn handle-update-indicator [state id new-indicator]
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

(defn handle-list-indicators-by-observable
  [state judgement-store observable]

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
    (raw-search-docs  (:conn state)
                      (:index state)
                      mapping
                      query
                      {:timestamp "desc"})))
