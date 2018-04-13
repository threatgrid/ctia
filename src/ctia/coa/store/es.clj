(ns ctia.coa.store.es
  (:require [ctia.schemas.core :refer [PartialStoredCOA StoredCOA]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store COAStore :coa StoredCOA PartialStoredCOA)
