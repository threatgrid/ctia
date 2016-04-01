(ns ctia.stores.atom.incident
  (:require [ctia.schemas.incident :refer [NewIncident StoredIncident realize-incident]]
            [ctia.stores.atom.common :as mc]))

(def swap-incident (mc/make-swap-fn realize-incident))

(mc/def-create-handler handle-create-incident
  StoredIncident NewIncident swap-incident (mc/random-id "incident"))

(mc/def-read-handler handle-read-incident StoredIncident)

(mc/def-delete-handler handle-delete-incident StoredIncident)

(mc/def-update-handler handle-update-incident
  StoredIncident NewIncident swap-incident)
