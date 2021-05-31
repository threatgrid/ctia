(ns ctia.entity.actor
  (:require
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.schemas.core :refer [APIHandlerServices def-acl-schema def-stored-schema]]
   [ctia.schemas.sorting :as sorting]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
   [ctim.schemas.actor :as as]
   [flanders.utils :as fu]
   [schema-tools.core :as st]
   [schema.core :as s]))

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

(def-stored-schema StoredActor Actor)

(s/defschema PartialStoredActor
  (st/optional-keys-schema StoredActor))

(def realize-actor
  (default-realize-fn "actor" NewActor StoredActor))

(def actor-mapping
  {"actor"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:valid_time                       em/valid-time
      :actor_type                       em/token
      :identity                         em/tg-identity
      :motivation                       em/token
      :sophistication                   em/token
      :intended_effect                  em/token
      :planning_and_operational_support em/token
      :related_indicators               em/related-indicators
      :confidence                       em/token})}})

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
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   ActorFieldsParam
   (st/optional-keys
    {:actor_type      s/Str
     :motivation      s/Str
     :sophistication  s/Str
     :intended_effect s/Str
     :confidence      s/Str
     :sort_by         actor-sort-fields})))

(def ActorGetParams ActorFieldsParam)

(s/defschema ActorByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   ActorFieldsParam))

(def actor-enumerable-fields
  [:source
   :actor_type
   :motivation
   :sophistication
   :confidence
   :intended_effect])

(def actor-histogram-fields
  [:timestamp
   :valid_time.start_time
   :valid_time.end_time])

(s/defn actor-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :actor
    :new-schema               NewActor
    :entity-schema            Actor
    :get-schema               PartialActor
    :get-params               ActorGetParams
    :list-schema              PartialActorList
    :search-schema            PartialActorList
    :external-id-q-params     ActorByExternalIdQueryParams
    :search-q-params          ActorSearchParams
    :new-spec                 :new-actor/map
    :realize-fn               realize-actor
    :get-capabilities         :read-actor
    :post-capabilities        :create-actor
    :put-capabilities         :create-actor
    :delete-capabilities      :delete-actor
    :search-capabilities      :search-actor
    :external-id-capabilities :read-actor
    :can-aggregate?           true
    :histogram-fields         actor-histogram-fields
    :enumerable-fields        actor-enumerable-fields
    :searchable-fields        (routes.common/searchable-fields
                               actor-fields)}))

(def capabilities
  #{:create-actor
    :read-actor
    :delete-actor
    :search-actor})

(def actor-entity
  {:route-context         "/actor"
   :tags                  ["Actor"]
   :entity                :actor
   :plural                :actors
   :new-spec              :new-actor/map
   :schema                Actor
   :partial-schema        PartialActor
   :partial-list-schema   PartialActorList
   :new-schema            NewActor
   :stored-schema         StoredActor
   :partial-stored-schema PartialStoredActor
   :realize-fn            realize-actor
   :es-store              ->ActorStore
   :es-mapping            actor-mapping
   :services->routes      (routes.common/reloadable-function actor-routes)
   :capabilities          capabilities
   :fields                actor-fields
   :sort-fields           actor-fields})
