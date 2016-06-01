(ns ctia.stores.atom.incident
  (:require [ctim.schemas.incident :refer [StoredIncident]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-incident (mc/create-handler-from-realized StoredIncident))
(def handle-read-incident (mc/read-handler StoredIncident))
(def handle-update-incident (mc/update-handler-from-realized StoredIncident))
(def handle-delete-incident (mc/delete-handler StoredIncident))
