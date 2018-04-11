(ns ctia.actor.routes
  (:require [ctia.domain.entities :refer [realize-actor]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [Actor NewActor PartialActor PartialActorList]]
             [sorting :as sorting]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def actor-sort-fields
  (apply s/enum sorting/actor-sort-fields))

(s/defschema ActorFieldsParam
  {(s/optional-key :fields) [actor-sort-fields]})

(s/defschema ActorSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   ActorFieldsParam
   {:query s/Str
    (s/optional-key :actor_type) s/Str
    (s/optional-key :motivation) s/Str
    (s/optional-key :sophistication) s/Str
    (s/optional-key :intended_effect) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :sort_by)  actor-sort-fields}))

(def ActorGetParams ActorFieldsParam)

(s/defschema ActorByExternalIdQueryParams
  (st/merge
   PagingParams
   ActorFieldsParam))

(def actor-routes
  (entity-crud-routes
   {:entity :actor
    :new-schema NewActor
    :entity-schema Actor
    :get-schema PartialActor
    :get-params ActorGetParams
    :list-schema PartialActorList
    :search-schema PartialActorList
    :external-id-q-params ActorByExternalIdQueryParams
    :search-q-params ActorSearchParams
    :new-spec :new-actor/map
    :realize-fn realize-actor
    :get-capabilities :read-actor
    :post-capabilities :create-actor
    :put-capabilities :create-actor
    :delete-capabilities :delete-actor
    :search-capabilities :search-actor
    :external-id-capabilities #{:read-actor :external-id}}))
