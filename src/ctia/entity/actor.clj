(ns ctia.entity.actor
  (:require
   [flanders.utils :as fu]
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.http.routes
    [common :refer [BaseEntityFilterParams
                    PagingParams
                    SourcableEntityFilterParams]]
    [crud :refer [entity-crud-routes]]]
   [ctia.schemas
    [core :refer [def-acl-schema def-stored-schema]]
    [sorting :refer [default-entity-sort-fields]]]
   [ctia.stores.es.store :refer [def-es-store]]
   [ctim.schemas.actor :as as]
   [schema-tools.core :as st]
   [schema.core :as s]
   [ctia.stores.es.mapping :as em]
   [ctia.schemas.sorting :as sorting]))

(def-acl-schema Actor
  as/Actor
  "actor")

(def-acl-schema PartialActor
  (fu/optionalize-all as/Actor)
  "partial-actor")

(s/defschema PartialActorList
  [PartialActor])

(def-acl-schema NewActor
  as/NewActor
  "new-actor")

(def-stored-schema StoredActor
  as/StoredActor
  "stored-actor")

(def-stored-schema PartialStoredActor
  (fu/optionalize-all as/StoredActor)
  "partial-stored-actor")

(def realize-actor
  (default-realize-fn "actor" NewActor StoredActor))

(def actor-mapping
  {"actor"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:valid_time em/valid-time
      :actor_type em/token
      :identity em/tg-identity
      :motivation em/token
      :sophistication em/token
      :intended_effect em/token
      :planning_and_operational_support em/token
      :related_indicators em/related-indicators
      :confidence em/token})}})

(def-es-store ActorStore :actor StoredActor PartialStoredActor)

(def actor-fields
  (concat sorting/default-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time
           :actor_type
           :motivation
           :sophistication
           :intended_effect]))

(def actor-sort-fields
  (apply s/enum actor-fields))

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

(def capabilities
  #{:create-actor
    :read-actor
    :delete-actor
    :search-actor})

(def actor-entity
  {:route-context "/actor"
   :tags ["Actor"]
   :entity :actor
   :plural :actors
   :schema Actor
   :partial-schema PartialActor
   :partial-list-schema PartialActorList
   :new-schema NewActor
   :stored-schema StoredActor
   :partial-stored-schema PartialStoredActor
   :realize-fn realize-actor
   :es-store ->ActorStore
   :es-mapping actor-mapping
   :routes actor-routes
   :capabilities capabilities})
