(ns ctia.stores.es.scratchpad
  (:require [ctia.schemas.core
             :refer [StoredScratchpad PartialStoredScratchpad]]
            [ctia.stores.es.crud :as crud]))

(def handle-create (crud/handle-create :scratchpad StoredScratchpad))
(def handle-read (crud/handle-read :scratchpad PartialStoredScratchpad))
(def handle-update (crud/handle-update :scratchpad StoredScratchpad))
(def handle-delete (crud/handle-delete :scratchpad StoredScratchpad))
(def handle-list (crud/handle-find :scratchpad PartialStoredScratchpad))
(def handle-query-string-search (crud/handle-query-string-search :scratchpad PartialStoredScratchpad))

(def ^{:private true} mapping "scratchpad")
