(ns ctia.stores.es.indicator
  (:require [ctia.lib.es.document :refer [search-docs]]
            [ctim.domain.id :as id]
            [ctia.schemas.core :refer [StoredIndicator]]
            [ctia.stores.es.crud :as crud]))

(def handle-create-indicator (crud/handle-create :indicator StoredIndicator))
(def handle-read-indicator (crud/handle-read :indicator StoredIndicator))
(def handle-update-indicator (crud/handle-update :indicator StoredIndicator))
(def handle-delete-indicator (crud/handle-delete :indicator StoredIndicator))
(def handle-list-indicators (crud/handle-find :indicator StoredIndicator))

(def ^{:private true} mapping "indicator")

(defn handle-list-indicators-by-judgements
  [state judgements params]
  (let [ids (some->> judgements
                     (map :indicators)
                     (mapcat #(map :indicator_id %))
                     (map #(id/str->short-id %))
                     set)]
    (when ids
      (search-docs (:conn state)
                   (:index state)
                   mapping
                   {:type "indicator"
                    :id (vec ids)}
                   params))))
