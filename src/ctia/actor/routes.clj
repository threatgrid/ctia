(ns ctia.actor.routes
  (:require
   [ctia.http.routes.crud :refer [entity-crud-routes]]
   [ctia.http.routes.common
    :refer [PagingParams
            BaseEntityFilterParams
            SourcableEntityFilterParams]]
   [ctia.schemas.sorting :as sorting]
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities
    :refer [realize-actor
            with-long-id
            page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created
            search-options
            filter-map-search-options
            paginated-ok]]
   [ctia.store :refer :all]
   [ctia.schemas.core
    :refer [NewActor Actor PartialActor PartialActorList]]
   [ring.util.http-response :refer [no-content not-found ok]]
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
