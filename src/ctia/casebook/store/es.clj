(ns ctia.casebook.store.es
  (:refer-clojure :exclude [read update list create])
  (:require [ctia.schemas.core :refer [PartialStoredCasebook StoredCasebook]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store CasebookStore :casebook StoredCasebook PartialStoredCasebook)
