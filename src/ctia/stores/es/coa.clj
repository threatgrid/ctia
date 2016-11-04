(ns ctia.stores.es.coa
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredCOA]]))

(def handle-create (crud/handle-create :coa StoredCOA))
(def handle-read (crud/handle-read :coa StoredCOA))
(def handle-update (crud/handle-update :coa StoredCOA))
(def handle-delete (crud/handle-delete :coa StoredCOA))
(def handle-list (crud/handle-find :coa StoredCOA))
(def handle-query-string-search (crud/handle-query-string-search :coa StoredCOA))
