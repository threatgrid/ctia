(ns ctia.entity.identity-assertion
  (:require
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.schemas.core :refer [APIHandlerServices def-acl-schema def-stored-schema]]
   [ctia.schemas.sorting :as sorting]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
   [ctim.schemas.identity-assertion :as assertion]
   [flanders.utils :as fu]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def-acl-schema IdentityAssertion
  assertion/IdentityAssertion
  "identity-assertion")

(def-acl-schema PartialIdentityAssertion
  (fu/optionalize-all assertion/IdentityAssertion)
  "partial-identity-assertion")

(s/defschema PartialIdentityAssertionList
  [PartialIdentityAssertion])

(def-acl-schema NewIdentityAssertion
  assertion/NewIdentityAssertion
  "new-identity-assertion")

(def-stored-schema StoredIdentityAssertion IdentityAssertion)

(s/defschema PartialStoredIdentityAssertion
  (st/optional-keys-schema StoredIdentityAssertion))

(def realize-identity-assertion
  (default-realize-fn "identity-assertion" NewIdentityAssertion StoredIdentityAssertion))

(def identity-assertion-mapping
  {"identity-assertion"
   {:dynamic false
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:identity em/identity
      :assertions em/assertion
      :valid_time em/valid-time})}})

(def-es-store IdentityAssertionStore :identity-assertion StoredIdentityAssertion PartialStoredIdentityAssertion)

(def identity-assertion-fields
  (concat sorting/base-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time]))

(def identity-assertion-sort-fields
  (apply s/enum identity-assertion-fields))

(s/defschema  IdentityAssertionFieldsParam
  {(s/optional-key :fields) [identity-assertion-sort-fields]})

(s/defschema IdentityAssertionSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   IdentityAssertionFieldsParam
   (st/optional-keys
    {:identity.observables.type  s/Str
     :identity.observables.value s/Str
     :assertions.name            s/Str
     :assertions.value           s/Str
     :sort_by                    identity-assertion-sort-fields})))

(def identity-assertion-histogram-fields
  [:timestamp
   :valid_time.start_time
   :valid_time.end_time])

(def identity-assertion-enumerable-fields
  [:identity.observables.value
   :identity.observables.type])

(def IdentityAssertionGetParams IdentityAssertionFieldsParam)

(s/defschema IdentityAssertionByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   IdentityAssertionFieldsParam))

(s/defn identity-assertion-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :identity-assertion
    :new-schema               NewIdentityAssertion
    :entity-schema            IdentityAssertion
    :get-schema               PartialIdentityAssertion
    :get-params               IdentityAssertionGetParams
    :list-schema              PartialIdentityAssertionList
    :search-schema            PartialIdentityAssertionList
    :external-id-q-params     IdentityAssertionByExternalIdQueryParams
    :search-q-params          IdentityAssertionSearchParams
    :realize-fn               realize-identity-assertion
    :get-capabilities         :read-identity-assertion
    :post-capabilities        :create-identity-assertion
    :put-capabilities         :create-identity-assertion
    :delete-capabilities      :delete-identity-assertion
    :search-capabilities      :search-identity-assertion
    :external-id-capabilities :read-identity-assertion
    :can-aggregate?           true
    :enumerable-fields        identity-assertion-enumerable-fields
    :histogram-fields         identity-assertion-histogram-fields
    :searchable-fields        (routes.common/searchable-fields
                               {:schema IdentityAssertion
                                :ignore [:valid_time.start_time
                                         :valid_time.end_time]})}))

(def capabilities
  #{:create-identity-assertion
    :read-identity-assertion
    :delete-identity-assertion
    :search-identity-assertion})

(def identity-assertion-entity
  {:route-context         "/identity-assertion"
   :tags                  ["Identity Assertion"]
   :entity                :identity-assertion
   :plural                :identity-assertions
   :new-spec              :new-identity-assertion/map
   :schema                IdentityAssertion
   :partial-schema        PartialIdentityAssertion
   :partial-list-schema   PartialIdentityAssertionList
   :new-schema            NewIdentityAssertion
   :stored-schema         StoredIdentityAssertion
   :partial-stored-schema PartialStoredIdentityAssertion
   :realize-fn            realize-identity-assertion
   :es-store              ->IdentityAssertionStore
   :es-mapping            identity-assertion-mapping
   :services->routes      (routes.common/reloadable-function identity-assertion-routes)
   :capabilities          capabilities
   :fields                identity-assertion-fields
   :sort-fields           identity-assertion-fields})
