(ns ctia.attack-pattern.store.es
  (:require [ctia.schemas.core
             :refer
             [PartialStoredAttackPattern StoredAttackPattern]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store AttackPatternStore :attack-pattern StoredAttackPattern PartialStoredAttackPattern)
