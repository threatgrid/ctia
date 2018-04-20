(ns ctia.entity.attack-pattern
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.entity.feedback.graphql-schemas :as feedback]
            [ctia.entity.relationship.graphql-schemas
             :as relationship-graphql]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.store :refer :all]
            [ctia.schemas
             [core :refer [def-acl-schema def-stored-schema]]
             [sorting :as sorting]]
            [ctia.schemas.graphql
             [flanders :as flanders]
             [helpers :as g]
             [pagination :as pagination]
             [sorting :as graphql-sorting]]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
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

(def-stored-schema StoredAttackPattern
  attack/StoredAttackPattern
  "stored-attack-pattern")

(def-stored-schema PartialStoredAttackPattern
  (fu/optionalize-all attack/StoredAttackPattern)
  "partial-stored-attack-pattern")

(def realize-attack-pattern
  (default-realize-fn "attack-pattern" NewAttackPattern StoredAttackPattern))

(def attack-pattern-fields
  (concat sorting/base-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:name]))

(def attack-pattern-sort-fields
  (apply s/enum attack-pattern-fields))

(def attack-pattern-mapping
  {"attack-pattern"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:abstraction_level em/token
      :name em/all_token
      :description em/all_text
      :kill_chain_phases em/kill-chain-phase
      :x_mitre_data_sources em/token
      :x_mitre_platforms em/token
      :x_mitre_contributors em/token})}})

(def-es-store AttackPatternStore :attack-pattern StoredAttackPattern PartialStoredAttackPattern)

(s/defschema AttackPatternFieldsParam
  {(s/optional-key :fields) [attack-pattern-sort-fields]})

(s/defschema AttackPatternSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   AttackPatternFieldsParam
   {:query s/Str}
   (st/optional-keys
    {:kill_chain_phases.kill_chain_name s/Str
     :kill_chain_phases.phase_name s/Str
     :sort_by attack-pattern-sort-fields})))

(s/defschema AttackPatternGetParams AttackPatternFieldsParam)

(s/defschema AttackPatternByExternalIdQueryParams
  (st/merge PagingParams AttackPatternFieldsParam))

(def attack-pattern-routes
  (entity-crud-routes
   {:entity :attack-pattern
    :new-schema NewAttackPattern
    :entity-schema AttackPattern
    :get-schema PartialAttackPattern
    :get-params AttackPatternGetParams
    :list-schema PartialAttackPatternList
    :search-schema PartialAttackPatternList
    :external-id-q-params AttackPatternByExternalIdQueryParams
    :search-q-params AttackPatternSearchParams
    :new-spec :new-attack-pattern/map
    :realize-fn realize-attack-pattern
    :get-capabilities :read-attack-pattern
    :post-capabilities :create-attack-pattern
    :put-capabilities :create-attack-pattern
    :delete-capabilities :delete-attack-pattern
    :search-capabilities :search-attack-pattern
    :external-id-capabilities #{:read-attack-pattern :external-id}}))

(def AttackPatternType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all attack/AttackPattern)
         {})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship-graphql/relatable-entity-fields))))

(def attack-pattern-order-arg
  (graphql-sorting/order-by-arg
   "AttackPatternOrder"
   "attack-patterns"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              attack-pattern-fields))))

(def AttackPatternConnectionType
  (pagination/new-connection AttackPatternType))

(def capabilities
  #{:create-attack-pattern
    :read-attack-pattern
    :delete-attack-pattern
    :search-attack-pattern})

(def attack-pattern-entity
  {:route-context "/attack-pattern"
   :tags ["Attack Pattern"]
   :entity :attack-pattern
   :plural :attack_patterns
   :schema AttackPattern
   :partial-schema PartialAttackPattern
   :partial-list-schema PartialAttackPatternList
   :new-schema NewAttackPattern
   :stored-schema StoredAttackPattern
   :partial-stored-schema PartialStoredAttackPattern
   :realize-fn realize-attack-pattern
   :es-store ->AttackPatternStore
   :es-mapping attack-pattern-mapping
   :routes attack-pattern-routes
   :capabilities capabilities})
