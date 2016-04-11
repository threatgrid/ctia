(ns ctia.stores.atom.incident
  (:require [ctia.schemas.incident :refer [NewIncident StoredIncident realize-incident]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-incident (mc/create-handler-from-realized StoredIncident))

(mc/def-read-handler handle-read-incident StoredIncident)

(mc/def-delete-handler handle-delete-incident StoredIncident)

(def handle-update-incident (mc/update-handler-from-realized StoredIncident))
