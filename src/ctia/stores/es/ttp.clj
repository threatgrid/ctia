(ns ctia.stores.es.ttp
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredTTP]]))

(def handle-create (crud/handle-create :ttp StoredTTP))
(def handle-read (crud/handle-read :ttp StoredTTP))
(def handle-update (crud/handle-update :ttp StoredTTP))
(def handle-delete (crud/handle-delete :ttp StoredTTP))
(def handle-list (crud/handle-find :ttp StoredTTP))
(def handle-query-string-search (crud/handle-query-string-search :ttp StoredTTP))
