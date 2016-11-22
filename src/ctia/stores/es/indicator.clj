(ns ctia.stores.es.indicator
  (:require [ctia.lib.es.document :refer [search-docs]]
            [ctim.domain.id :as id]
            [ctia.schemas.core :refer [StoredIndicator]]
            [ctia.stores.es.crud :as crud]))

(def handle-create (crud/handle-create :indicator StoredIndicator))
(def handle-read (crud/handle-read :indicator StoredIndicator))
(def handle-update (crud/handle-update :indicator StoredIndicator))
(def handle-delete (crud/handle-delete :indicator StoredIndicator))
(def handle-list (crud/handle-find :indicator StoredIndicator))
(def handle-query-string-search (crud/handle-query-string-search :indicator StoredIndicator))

(def ^{:private true} mapping "indicator")
