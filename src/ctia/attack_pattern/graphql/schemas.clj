(ns ctia.attack-pattern.graphql.schemas
  (:require
   [flanders.utils :as fu]
   [ctia.feedback.graphql.schemas :as feedback]
   [ctia.relationship.graphql.schemas :as relationship]
   [ctia.schemas.graphql
    [flanders :as flanders]
    [helpers :as g]
    [pagination :as pagination]
    [refs :as refs]
    [sorting :as sorting]]
   [ctia.schemas.sorting :as sort-fields]
   [ctim.schemas.attack-pattern :as ctim-attack-pattern]))

(def AttackPatternType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all ctim-attack-pattern/AttackPattern)
         {})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship/relatable-entity-fields))))

(def attack-pattern-order-arg
  (sorting/order-by-arg
   "AttackPatternOrder"
   "attack-patterns"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              sort-fields/attack-pattern-sort-fields))))

(def AttackPatternConnectionType
  (pagination/new-connection AttackPatternType))

