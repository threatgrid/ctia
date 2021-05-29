(ns ctia.entity.attack-pattern
  (:require
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.entity.feedback.graphql-schemas :as feedback]
   [ctia.entity.relationship.graphql-schemas :as relationship-graphql]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.schemas.core :refer [APIHandlerServices def-acl-schema def-stored-schema]]
   [ctia.schemas.graphql.flanders :as flanders]
   [ctia.schemas.graphql.helpers :as g]
   [ctia.schemas.graphql.ownership :as go]
   [ctia.schemas.graphql.pagination :as pagination]
   [ctia.schemas.graphql.sorting :as graphql-sorting]
   [ctia.schemas.sorting :as sorting]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
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

(def realize-attack-pattern
  (default-realize-fn "attack-pattern" NewAttackPattern StoredAttackPattern))

(def attack-pattern-fields sorting/default-entity-sort-fields)

(def attack-pattern-sort-fields
  (apply s/enum attack-pattern-fields))

(def attack-pattern-mapping
  {"attack-pattern"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/sourcable-entity-mapping
     em/describable-entity-mapping
     em/stored-entity-mapping
     {:abstraction_level    em/token
      :kill_chain_phases    em/kill-chain-phase
      :x_mitre_data_sources em/token
      :x_mitre_platforms    em/token
      :x_mitre_contributors em/token})}})

(def-es-store AttackPatternStore :attack-pattern StoredAttackPattern PartialStoredAttackPattern)

(s/defschema AttackPatternFieldsParam
  {(s/optional-key :fields) [attack-pattern-sort-fields]})

(s/defschema AttackPatternSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   AttackPatternFieldsParam
   (st/optional-keys
    {:kill_chain_phases.kill_chain_name s/Str
     :kill_chain_phases.phase_name      s/Str
     :sort_by                           attack-pattern-sort-fields})))

(s/defschema AttackPatternGetParams AttackPatternFieldsParam)

(s/defschema AttackPatternByExternalIdQueryParams
  (st/merge routes.common/PagingParams AttackPatternFieldsParam))

(def attack-pattern-enumerable-fields
  [:source])

(def attack-pattern-histogram-fields
  [:timestamp])

(s/defn attack-pattern-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :attack-pattern
    :new-schema               NewAttackPattern
    :entity-schema            AttackPattern
    :get-schema               PartialAttackPattern
    :get-params               AttackPatternGetParams
    :list-schema              PartialAttackPatternList
    :search-schema            PartialAttackPatternList
    :external-id-q-params     AttackPatternByExternalIdQueryParams
    :search-q-params          AttackPatternSearchParams
    :new-spec                 :new-attack-pattern/map
    :realize-fn               realize-attack-pattern
    :get-capabilities         :read-attack-pattern
    :post-capabilities        :create-attack-pattern
    :put-capabilities         :create-attack-pattern
    :delete-capabilities      :delete-attack-pattern
    :search-capabilities      :search-attack-pattern
    :external-id-capabilities :read-attack-pattern
    :can-aggregate?           true
    :histogram-fields         attack-pattern-histogram-fields
    :enumerable-fields        attack-pattern-enumerable-fields}))

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
            relationship-graphql/relatable-entity-fields
            go/graphql-ownership-fields))))

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
  {:route-context         "/attack-pattern"
   :tags                  ["Attack Pattern"]
   :entity                :attack-pattern
   :plural                :attack-patterns
   :new-spec              :new-attack-pattern/map
   :schema                AttackPattern
   :partial-schema        PartialAttackPattern
   :partial-list-schema   PartialAttackPatternList
   :new-schema            NewAttackPattern
   :stored-schema         StoredAttackPattern
   :partial-stored-schema PartialStoredAttackPattern
   :realize-fn            realize-attack-pattern
   :es-store              ->AttackPatternStore
   :es-mapping            attack-pattern-mapping
   :services->routes      (routes.common/reloadable-function attack-pattern-routes)
   :capabilities          capabilities
   :fields                attack-pattern-fields
   :sort-fields           attack-pattern-fields
   :searchable-fields     (routes.common/searchable-fields
                           attack-pattern-entity)})
