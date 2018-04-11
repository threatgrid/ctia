(ns ctia.relationship.routes
  (:require [ctia.domain.entities :refer [realize-relationship]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [NewRelationship PartialRelationship PartialRelationshipList Relationship]]
             [sorting :as sorting]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def relationship-sort-fields
  (apply s/enum sorting/relationship-sort-fields))

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
    :new-schema NewRelationship
    :entity-schema Relationship
    :get-schema PartialRelationship
    :get-params RelationshipGetParams
    :list-schema PartialRelationshipList
    :search-schema PartialRelationshipList
    :external-id-q-params RelationshipByExternalIdQueryParams
    :search-q-params RelationshipSearchParams
    :new-spec :new-relationship/map
    :realize-fn realize-relationship
    :get-capabilities :read-relationship
    :post-capabilities :create-relationship
    :put-capabilities :create-relationship
    :delete-capabilities :delete-relationship
    :search-capabilities :search-relationship
    :external-id-capabilities #{:read-relationship :external-id}}))
