(ns ctia.stores.es.indicator
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [ctia.stores.es.crud :as crud]
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

(def handle-create-indicator (crud/handle-create :indicator StoredIndicator))
(def handle-read-indicator (crud/handle-read :indicator StoredIndicator))
(def handle-update-indicator (crud/handle-update :indicator StoredIndicator))
(def handle-delete-indicator (crud/handle-delete :indicator StoredIndicator))
(def handle-list-indicators (crud/handle-find :indicator StoredIndicator))

(def ^{:private true} mapping "indicator")


(defn handle-list-indicators-by-judgements
  [state judgements]
  (let [ids (some->> judgements
                     (map :indicators)
                     (mapcat #(map :indicator_id %))
                     set)]
    (when ids
      (search-docs (:conn state)
                   (:index state)
                   mapping
                   {:type "indicator"
                    :id (vec ids)}))))
