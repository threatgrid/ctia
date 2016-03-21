(ns ctia.stores.memory.incident
  (:require [ctia.schemas.incident :refer [NewIncident StoredIncident realize-incident]]
            [ctia.store :refer [IIncidentStore]]
            [ctia.stores.memory.common :as mc]))

(def swap-incident (mc/make-swap-fn realize-incident))

(mc/def-create-handler handle-create-incident
  StoredIncident NewIncident swap-incident (mc/random-id "incident"))

(mc/def-read-handler handle-read-incident StoredIncident)

(mc/def-delete-handler handle-delete-incident StoredIncident)

(mc/def-update-handler handle-update-incident
  StoredIncident NewIncident swap-incident)

(defrecord IncidentStore [state]
  IIncidentStore
  (read-incident [_ id]
    (handle-read-incident state id))
  (create-incident [_ login new-incident]
    (handle-create-incident state login new-incident))
  (update-incident [_ id login incident]
    (handle-update-incident state id login incident))
  (delete-incident [_ id]
    (handle-delete-incident state id))
  (list-incidents [_ filter-map]))
