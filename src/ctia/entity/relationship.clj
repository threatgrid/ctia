(ns ctia.entity.relationship
  (:require [compojure.api.sweet :refer [POST]]
            [ctia
             [properties :refer [get-http-show]]
             [store :refer :all]]
            [ctia.domain.entities :refer [un-store with-long-id]]
            [ctia.entity.relationship.schemas :as rs]
            [ctia.flows.crud :as flows]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams created PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [Reference TLP]]
             [sorting :as sorting]]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [ctim.domain.id :refer [long-id->id short-id->long-id]]
            [ring.util.http-response :refer [not-found bad-request]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def relationship-mapping
  {"relationship"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:relationship_type em/token
      :source_ref em/token
      :target_ref em/token})}})

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
   (st/optional-keys
    {:query s/Str
     :relationship_type s/Str
     :source_ref s/Str
     :target_ref s/Str
     :sort_by  relationship-sort-fields})))

(s/defschema RelationshipGetParams RelationshipFieldsParam)

(s/defschema RelationshipByExternalIdQueryParams
  (st/merge PagingParams
            RelationshipFieldsParam))

(s/defschema IncidentCasebookLinkRequest
  {:casebook_id Reference
   (s/optional-key :tlp) TLP})

(def incident-casebook-link-route
  (POST "/:id/link" []
        :return rs/Relationship
        :body [link-req IncidentCasebookLinkRequest
               {:description "an Incident Link request"}]
        :summary "Link an Incident to a Casebook"
        :path-params [id :- s/Str]
        :capabilities #{:read-incident
                        :read-casebook
                        :read-relationship
                        :create-relationship}
        :auth-identity identity
        :identity-map identity-map
        (let [incident (read-store :incident
                                   read-record
                                   id
                                   identity-map
                                   {})
              casebook-id (-> link-req
                              :casebook_id
                              long-id->id
                              :short-id)
              casebook (when casebook-id
                         (read-store :casebook
                                     read-record
                                     casebook-id
                                     identity-map
                                     {}))
              target-ref (short-id->long-id id
                                            get-http-show)]
          (cond
            (or (not incident)
                (not target-ref))
            (not-found {:error "Invalid Incident id"})
            (not casebook)
            (bad-request {:error "Invalid Casebook id"})
            :else
            (let [{:keys [tlp casebook_id]
                   :or {tlp "amber"}} link-req
                  new-relationship
                  {:source_ref casebook_id
                   :target_ref target-ref
                   :relationship_type "related-to"
                   :tlp tlp}
                  stored-relationship
                  (-> (flows/create-flow
                       :entity-type :relationship
                       :realize-fn rs/realize-relationship
                       :store-fn #(write-store :relationship
                                               create-record
                                               %
                                               identity-map
                                               {})
                       :long-id-fn with-long-id
                       :entity-type :relationship
                       :identity identity
                       :entities [new-relationship]
                       :spec :new-relationship/map)
                      first
                      un-store)]
              (created stored-relationship))))))

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
    :external-id-capabilities :read-relationship}))

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
   :new-spec :new-relationship/map
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
