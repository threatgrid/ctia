(ns ctia.incident.store.es
  (:require [ctia.schemas.core :refer [PartialStoredIncident StoredIncident]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store IncidentStore :incident StoredIncident PartialStoredIncident)
