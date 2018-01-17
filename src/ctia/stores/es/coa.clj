(ns ctia.stores.es.coa
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core
             :refer [StoredCOA PartialStoredCOA]]))

(def handle-create (crud/handle-create :coa StoredCOA))
(def handle-read (crud/handle-read :coa PartialStoredCOA))
(def handle-update (crud/handle-update :coa StoredCOA))
(def handle-delete (crud/handle-delete :coa StoredCOA))
(def handle-list (crud/handle-find :coa PartialStoredCOA))
(def handle-query-string-search (crud/handle-query-string-search :coa PartialStoredCOA))
