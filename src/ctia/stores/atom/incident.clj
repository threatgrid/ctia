(ns ctia.stores.atom.incident
  (:require [ctia.schemas.core :refer [StoredIncident]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-incident (mc/create-handler-from-realized StoredIncident))
(def handle-read-incident (mc/read-handler StoredIncident))
(def handle-list-incidents (mc/list-handler StoredIncident))
(def handle-update-incident (mc/update-handler-from-realized StoredIncident))
(def handle-delete-incident (mc/delete-handler StoredIncident))
