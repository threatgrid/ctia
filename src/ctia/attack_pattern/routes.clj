(ns ctia.attack-pattern.routes
  (:require
   [ctia.domain.entities :refer [realize-attack-pattern]]
   [ctia.http.routes
    [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
    [crud :refer [entity-crud-routes]]]
   [ctia.schemas
    [core :refer [AttackPattern NewAttackPattern PartialAttackPattern PartialAttackPatternList]]
    [sorting :as sorting]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def attack-pattern-sort-fields
  (apply s/enum sorting/attack-pattern-sort-fields))

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
