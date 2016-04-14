(ns ctia.stores.es.incident
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.incident :refer [StoredIncident]]))

(def handle-create-incident (crud/handle-create :incident StoredIncident))
(def handle-read-incident (crud/handle-read :incident StoredIncident))
(def handle-update-incident (crud/handle-update :incident StoredIncident))
(def handle-delete-incident (crud/handle-delete :incident StoredIncident))
(def handle-list-incidents (crud/handle-find :incident StoredIncident))
