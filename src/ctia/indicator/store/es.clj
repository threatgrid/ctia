(ns ctia.indicator.store.es
  (:require [ctia.schemas.core :refer [PartialStoredIndicator StoredIndicator]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store IndicatorStore :indicator StoredIndicator PartialStoredIndicator)
