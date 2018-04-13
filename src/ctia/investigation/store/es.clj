(ns ctia.investigation.store.es
  (:require [ctia.schemas.core :refer [PartialStoredInvestigation StoredInvestigation]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store InvestigationStore :investigation StoredInvestigation PartialStoredInvestigation)
