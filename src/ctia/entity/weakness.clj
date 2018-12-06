(ns ctia.entity.weakness
  (:require
   [flanders.utils :as fu]
   [ctia.entity.feedback.graphql-schemas :as feedback]
   [ctia.entity.relationship.graphql-schemas :as relationship]
   [ctia.entity.weakness.mapping :refer [weakness-mapping]]
   [ctia.store :refer :all]
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.schemas.graphql
    [sorting :as graphql-sorting]
    [flanders :as flanders]
    [helpers :as g]
    [pagination :as pagination]]
   [ctia.http.routes
    [common :refer [BaseEntityFilterParams
                    PagingParams
                    SourcableEntityFilterParams]]
    [crud :refer [entity-crud-routes]]]
   [ctia.schemas
    [utils :as csu]
    [core :refer [def-acl-schema def-stored-schema]]
    [sorting :refer [default-entity-sort-fields]]]
   [ctia.stores.es.store :refer [def-es-store]]
   [ctim.schemas.weakness :as ws]
   [schema-tools.core :as st]
   [schema.core :as s]
   [ctia.schemas.sorting :as sorting]))

(def-acl-schema Weakness
  ws/Weakness
  "weakness")

(def-acl-schema PartialWeakness
  (fu/optionalize-all ws/Weakness)
  "partial-weakness")

(s/defschema PartialWeaknessList
  [PartialWeakness])

(def-acl-schema NewWeakness
  ws/NewWeakness
  "new-weakness")

(def-stored-schema StoredWeakness Weakness)

(s/defschema PartialStoredWeakness
  (csu/optional-keys-schema StoredWeakness))

(def realize-weakness
  (default-realize-fn "weakness" NewWeakness StoredWeakness))

(def-es-store WeaknessStore :weakness StoredWeakness PartialStoredWeakness)

(def weakness-fields
  (concat sorting/base-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:detection_methods.method
           :background_details
           :architectures.name
           :architectures.class
           :affected_resources
           :languages.name
           :languages.class
           :paradigms.name
           :functional_areas
           :operating_systems.name
           :operating_systems.version
           :operating_systems.class
           :operating_systems.cpe_id
           :likelihood
           :technologies.name
           :technologies.prevalence]))

(def weakness-sort-fields
  (apply s/enum weakness-fields))

(s/defschema WeaknessFieldsParam
  {(s/optional-key :fields) [weakness-sort-fields]})

(s/defschema WeaknessSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   WeaknessFieldsParam
   {:query s/Str
    (s/optional-key :sort_by)  weakness-sort-fields}))

(def WeaknessGetParams WeaknessFieldsParam)

(s/defschema WeaknessByExternalIdQueryParams
  (st/merge
   PagingParams
   WeaknessFieldsParam))

(def WeaknessType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all ws/Weakness)
         {})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship/relatable-entity-fields))))

(def weakness-order-arg
  (graphql-sorting/order-by-arg
   "WeaknessOrder"
   "Weaknesses"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              weakness-fields))))

(def WeaknessConnectionType
  (pagination/new-connection WeaknessType))

(def weakness-routes
  (entity-crud-routes
   {:entity :weakness
    :new-schema NewWeakness
    :entity-schema Weakness
    :get-schema PartialWeakness
    :get-params WeaknessGetParams
    :list-schema PartialWeaknessList
    :search-schema PartialWeaknessList
    :external-id-q-params WeaknessByExternalIdQueryParams
    :search-q-params WeaknessSearchParams
    :new-spec :new-weakness/map
    :realize-fn realize-weakness
    :get-capabilities :read-weakness
    :post-capabilities :create-weakness
    :put-capabilities :create-weakness
    :delete-capabilities :delete-weakness
    :search-capabilities :search-weakness
    :external-id-capabilities #{:read-weakness :external-id}}))

(def capabilities
  #{:create-weakness
    :read-weakness
    :delete-weakness
    :search-weakness})

(def weakness-entity
  {:route-context "/weakness"
   :tags ["Weakness"]
   :entity :weakness
   :plural :weaknesses
   :schema Weakness
   :partial-schema PartialWeakness
   :partial-list-schema PartialWeaknessList
   :new-schema NewWeakness
   :stored-schema StoredWeakness
   :partial-stored-schema PartialStoredWeakness
   :realize-fn realize-weakness
   :es-store ->WeaknessStore
   :es-mapping weakness-mapping
   :routes weakness-routes
   :capabilities capabilities})
