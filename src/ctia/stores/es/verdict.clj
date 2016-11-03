(ns ctia.stores.es.verdict
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredVerdict]]))

(def handle-create (crud/handle-create :verdict StoredVerdict))
(def handle-read (crud/handle-read :verdict StoredVerdict))
(def handle-delete (crud/handle-delete :verdict StoredVerdict))
(def handle-list (crud/handle-find :verdict StoredVerdict))
