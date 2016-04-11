(ns ctia.stores.atom.incident
  (:require [ctia.schemas.incident :refer [NewIncident StoredIncident realize-incident]]
            [ctia.stores.atom.common :as mc]))

(def swap-incident (mc/make-swap-fn realize-incident))

(mc/def-create-handler-from-realized handle-create-incident StoredIncident)

(mc/def-read-handler handle-read-incident StoredIncident)

(mc/def-delete-handler handle-delete-incident StoredIncident)

(mc/def-update-handler-from-realized handle-update-incident StoredIncident)
