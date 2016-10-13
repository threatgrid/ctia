(ns ctia.stores.es.verdict
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredVerdict]]))

(def handle-create-verdict (crud/handle-create :verdict StoredVerdict))
(def handle-read-verdict (crud/handle-read :verdict StoredVerdict))
(def handle-delete-verdict (crud/handle-delete :verdict StoredVerdict))
(def handle-list-verdicts (crud/handle-find :verdict StoredVerdict))
