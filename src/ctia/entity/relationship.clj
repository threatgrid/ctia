(ns ctia.entity.relationship
  (:require [ctia.entity.relationship.schemas :as rs]
            [ctia.store :refer :all]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas.sorting :as sorting]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def relationship-mapping
  {"relationship"
   {:dynamic false
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:relationship_type em/token
      :source_ref em/all_token
      :target_ref em/all_token})}})

(def-es-store RelationshipStore
  :relationship
  rs/StoredRelationship
  rs/PartialStoredRelationship)

(def relationship-fields
  (concat sorting/default-entity-sort-fields
          sorting/describable-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:relationship_type
           :source_ref
           :target_ref]))

(def relationship-sort-fields
  (apply s/enum relationship-fields))

(s/defschema RelationshipFieldsParam
  {(s/optional-key :fields) [relationship-sort-fields]})

(s/defschema RelationshipSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   RelationshipFieldsParam
   {:query s/Str
    (s/optional-key :relationship_type) s/Str
    (s/optional-key :source_ref) s/Str
    (s/optional-key :target_ref) s/Str
    (s/optional-key :sort_by)  relationship-sort-fields}))

(s/defschema RelationshipGetParams RelationshipFieldsParam)

(s/defschema RelationshipByExternalIdQueryParams
  (st/merge PagingParams
            RelationshipFieldsParam))

(def relationship-routes
  (entity-crud-routes
   {:entity :relationship
    :new-schema rs/NewRelationship
    :entity-schema rs/Relationship
    :get-schema rs/PartialRelationship
    :get-params RelationshipGetParams
    :list-schema rs/PartialRelationshipList
    :search-schema rs/PartialRelationshipList
    :external-id-q-params RelationshipByExternalIdQueryParams
    :search-q-params RelationshipSearchParams
    :new-spec :new-relationship/map
    :realize-fn rs/realize-relationship
    :get-capabilities :read-relationship
    :post-capabilities :create-relationship
    :put-capabilities :create-relationship
    :delete-capabilities :delete-relationship
    :search-capabilities :search-relationship
    :external-id-capabilities #{:read-relationship :external-id}}))

(def capabilities
  #{:create-relationship
    :read-relationship
    :list-relationships
    :delete-relationship
    :search-relationship})

(def relationship-entity
  {:route-context "/relationship"
   :tags ["Relationship"]
   :entity :relationship
   :plural :relationships
   :schema rs/Relationship
   :partial-schema rs/PartialRelationship
   :partial-list-schema rs/PartialRelationshipList
   :new-schema rs/NewRelationship
   :stored-schema rs/StoredRelationship
   :partial-stored-schema rs/PartialStoredRelationship
   :realize-fn rs/realize-relationship
   :es-store ->RelationshipStore
   :es-mapping relationship-mapping
   :routes relationship-routes
   :capabilities capabilities})
