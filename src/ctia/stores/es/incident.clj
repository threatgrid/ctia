(ns ctia.stores.es.incident
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core
             :refer [StoredIncident PartialStoredIncident]]))

(def handle-create (crud/handle-create :incident StoredIncident))
(def handle-read (crud/handle-read :incident PartialStoredIncident))
(def handle-update (crud/handle-update :incident StoredIncident))
(def handle-delete (crud/handle-delete :incident StoredIncident))
(def handle-list (crud/handle-find :incident PartialStoredIncident))
(def handle-query-string-search (crud/handle-query-string-search :incident PartialStoredIncident))
