(ns ctia.entity.attack-pattern.schemas
  (:require [ctia.schemas.core :refer [def-acl-schema def-stored-schema]]
            [ctim.schemas.attack-pattern :as attack]
            [flanders.utils :as fu]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def-acl-schema AttackPattern
  attack/AttackPattern
  "attack-pattern")

(def-acl-schema PartialAttackPattern
  (fu/optionalize-all attack/AttackPattern)
  "partial-attack-pattern")

(s/defschema PartialAttackPatternList
  [PartialAttackPattern])

(def-acl-schema NewAttackPattern
  attack/NewAttackPattern
  "new-attack-pattern")

(def-stored-schema StoredAttackPattern AttackPattern)

(s/defschema PartialStoredAttackPattern
  (st/optional-keys-schema StoredAttackPattern))
