(ns ctia.entity.weakness
  (:require
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.entity.feedback.graphql-schemas :as feedback]
   [ctia.entity.relationship.graphql-schemas :as relationship]
   [ctia.entity.weakness.mapping :refer [weakness-mapping]]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.schemas.core :refer [APIHandlerServices def-acl-schema def-stored-schema]]
   [ctia.schemas.graphql.flanders :as flanders]
   [ctia.schemas.graphql.helpers :as g]
   [ctia.schemas.graphql.ownership :as go]
   [ctia.schemas.graphql.pagination :as pagination]
   [ctia.schemas.graphql.sorting :as graphql-sorting]
   [ctia.schemas.sorting :as sorting]
   [ctia.stores.es.store :refer [def-es-store]]
   [ctim.schemas.weakness :as ws]
   [flanders.utils :as fu]
   [schema-tools.core :as st]
   [schema.core :as s]))

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
  (st/optional-keys-schema StoredWeakness))

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
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   WeaknessFieldsParam
   (st/optional-keys
    {:sort_by weakness-sort-fields})))

(def WeaknessGetParams WeaknessFieldsParam)

(s/defschema WeaknessByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
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
            relationship/relatable-entity-fields
            go/graphql-ownership-fields))))

(def weakness-order-arg
  (graphql-sorting/order-by-arg
   "WeaknessOrder"
   "Weaknesses"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              weakness-fields))))

(def WeaknessConnectionType
  (pagination/new-connection WeaknessType))

(def weakness-histogram-fields
  [:timestamp])

(def weakness-enumerable-fields
  [:source
   :detection_methods.method
   :architectures.name
   :architectures.class
   :languages.name
   :languages.class
   :paradigms.name
   :functional_areas
   :operating_systems.name
   :operating_systems.class
   :likelihood
   :technologies.name
   :technologies.prevalence])

(def searchable-fields
  #{:id
    :source
    :paradigms.name
    :description
    :technologies.name
    :background_details
    :abstraction_level
    :languages.name
    :architectures.prevalence
    :common_consequences.scopes
    :alternate_terms.term
    :detection_methods.method
    :architectures.name
    :operating_systems.version
    :potential_mitigations.effectiveness_notes
    :likelihood
    :potential_mitigations.effectiveness
    :paradigms.prevalence
    :languages.prevalence
    :modes_of_introduction.phase
    :external_references.source_name
    :external_ids
    :short_description
    :common_consequences.likelihood
    :notes.note
    :title
    :operating_systems.name
    :operating_systems.cpe_id
    :functional_areas
    :notes.type
    :operating_systems.prevalence
    :structure
    :external_references.description
    :technologies.prevalence
    :external_references.hashes
    :affected_resources
    :potential_mitigations.phases
    :alternate_terms.description
    :external_references.external_id
    :potential_mitigations.strategy
    :detection_methods.effectiveness_notes
    :architectures.class
    :languages.class
    :potential_mitigations.description
    :common_consequences.note
    :detection_methods.effectiveness
    :common_consequences.impacts
    :operating_systems.class
    :modes_of_introduction.note
    :detection_methods.description})

(s/defn weakness-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:entity                   :weakness
    :new-schema               NewWeakness
    :entity-schema            Weakness
    :get-schema               PartialWeakness
    :get-params               WeaknessGetParams
    :list-schema              PartialWeaknessList
    :search-schema            PartialWeaknessList
    :external-id-q-params     WeaknessByExternalIdQueryParams
    :search-q-params          WeaknessSearchParams
    :new-spec                 :new-weakness/map
    :realize-fn               realize-weakness
    :get-capabilities         :read-weakness
    :post-capabilities        :create-weakness
    :put-capabilities         :create-weakness
    :delete-capabilities      :delete-weakness
    :search-capabilities      :search-weakness
    :external-id-capabilities :read-weakness
    :can-aggregate?           true
    :histogram-fields         weakness-histogram-fields
    :enumerable-fields        weakness-enumerable-fields
    :searchable-fields        searchable-fields}))

(def capabilities
  #{:create-weakness
    :read-weakness
    :delete-weakness
    :search-weakness})

(def weakness-entity
  {:route-context         "/weakness"
   :tags                  ["Weakness"]
   :entity                :weakness
   :plural                :weaknesses
   :new-spec              :new-weakness/map
   :schema                Weakness
   :partial-schema        PartialWeakness
   :partial-list-schema   PartialWeaknessList
   :new-schema            NewWeakness
   :stored-schema         StoredWeakness
   :partial-stored-schema PartialStoredWeakness
   :realize-fn            realize-weakness
   :es-store              ->WeaknessStore
   :es-mapping            weakness-mapping
   :services->routes      (routes.common/reloadable-function weakness-routes)
   :capabilities          capabilities
   :fields                weakness-fields
   :sort-fields           weakness-fields})
