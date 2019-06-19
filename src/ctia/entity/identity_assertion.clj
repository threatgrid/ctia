(ns ctia.entity.identity-assertion
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [utils :as csu]
             [core :refer [def-acl-schema def-stored-schema]]
             [sorting :as sorting]]
            [ctia.store :refer :all]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
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
  (csu/optional-keys-schema StoredIdentityAssertion))

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
     {:identity em/sighting-target
      :assertions em/assertion
      :valid_time em/valid-time})}})

(def-es-store IdentityAssertionStore :identity-assertion StoredIdentityAssertion PartialStoredIdentityAssertion)

(def identity-assertion-fields
  (concat sorting/base-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time
           :identity-assertion.assertions.name
           :identity-assertion.assertions.value
           :identity.observables.type
           :identity.observables.value]))

(def identity-assertion-sort-fields
  (apply s/enum identity-assertion-fields))

(s/defschema  IdentityAssertionFieldsParam
  {(s/optional-key :fields) [identity-assertion-sort-fields]})

(s/defschema IdentityAssertionSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   IdentityAssertionFieldsParam
   {:query s/Str
    (s/optional-key :identity.observables.type) s/Str
    (s/optional-key :identity.observables.value) s/Str
    (s/optional-key :assertions.name) s/Str
    (s/optional-key :assertions.value) s/Str
    (s/optional-key :sort_by) identity-assertion-sort-fields}))

(def IdentityAssertionGetParams IdentityAssertionFieldsParam)

(s/defschema IdentityAssertionByExternalIdQueryParams
  (st/merge
   PagingParams
   IdentityAssertionFieldsParam))

(def identity-assertion-routes
  (entity-crud-routes
   {:entity :identity-assertion
    :new-schema NewIdentityAssertion
    :entity-schema IdentityAssertion
    :get-schema PartialIdentityAssertion
    :get-params IdentityAssertionGetParams
    :list-schema PartialIdentityAssertionList
    :search-schema PartialIdentityAssertionList
    :external-id-q-params IdentityAssertionByExternalIdQueryParams
    :search-q-params IdentityAssertionSearchParams
    :realize-fn realize-identity-assertion
    :get-capabilities :read-identity-assertion
    :post-capabilities :create-identity-assertion
    :put-capabilities :create-identity-assertion
    :delete-capabilities :delete-identity-assertion
    :search-capabilities :search-identity-assertion
    :external-id-capabilities :read-identity-assertion}))

(def capabilities
  #{:create-identity-assertion
    :read-identity-assertion
    :delete-identity-assertion
    :search-identity-assertion})

(def identity-assertion-entity
  {:route-context "/identity-assertion"
   :tags ["IdentityAssertion"]
   :entity :identity-assertion
   :plural :identity-assertions
   :new-spec :new-identity-assertion/map
   :schema IdentityAssertion
   :partial-schema PartialIdentityAssertion
   :partial-list-schema PartialIdentityAssertionList
   :new-schema NewIdentityAssertion
   :stored-schema StoredIdentityAssertion
   :partial-stored-schema PartialStoredIdentityAssertion
   :realize-fn realize-identity-assertion
   :es-store ->IdentityAssertionStore
   :es-mapping identity-assertion-mapping
   :routes identity-assertion-routes
   :capabilities capabilities})
