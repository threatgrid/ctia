(ns ctia.stores.es.indicator
  (:require [ctia.schemas.core
             :refer [StoredIndicator PartialStoredIndicator]]
            [ctia.stores.es.crud :as crud]))

(def handle-create (crud/handle-create :indicator StoredIndicator))
(def handle-read (crud/handle-read :indicator PartialStoredIndicator))
(def handle-update (crud/handle-update :indicator StoredIndicator))
(def handle-delete (crud/handle-delete :indicator StoredIndicator))
(def handle-list (crud/handle-find :indicator PartialStoredIndicator))
(def handle-query-string-search (crud/handle-query-string-search :indicator PartialStoredIndicator))

(def ^{:private true} mapping "indicator")
