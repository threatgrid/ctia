(ns ctia.entity.relationship
  (:require [clojure.string :as str]
            [compojure.api.sweet :refer [POST]]
            [ctia
             [properties :refer [get-http-show]]
             [store :refer :all]]
            [ctia.domain.entities :refer [un-store with-long-id]]
            [ctia.entity.relationship.schemas :as rs]
            [ctia.flows.crud :as flows]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams created PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.http.middleware.auth :refer [require-capability!]]
            [ctia.schemas
             [core :refer [Reference TLP]]
             [sorting :as sorting]]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [ctim.domain.id :refer [long-id->id short-id->long-id]]
            [ring.util.http-response :refer [not-found bad-request bad-request!]]
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

(s/defschema IncidentLinkRequestOptional
  {(s/optional-key :tlp) TLP})

(def incident-link-source-types
  [:casebook_id
   :investigation_id])

(s/defschema IncidentLinkRequest
  (st/merge
    ;; Exactly one of these fields is required for a
    ;; valid request.
    (into {}
          (map (fn [k]
                 [(s/optional-key k) Reference]))
          incident-link-source-types)
    IncidentLinkRequestOptional))

(defn incident-link-route [apply-hooks apply-event-hooks]
  (POST "/:id/link" []
        :return rs/Relationship
        :body [link-req IncidentLinkRequest
               {:description "an Incident Link request"}]
        :summary "Link an Incident to a Casebook or Investigation"
        :path-params [id :- s/Str]
        ;; :read-<source-type> is checked while processing the body
        :capabilities #{:read-incident
                        :read-relationship
                        :create-relationship}
        :auth-identity identity
        :identity-map identity-map
        (let [source-type-kw (let [one-of-kws [:casebook_id
                                               :investigation_id]
                                   provided-types (select-keys link-req one-of-kws)]
                               (when-not (= 1 (count provided-types))
                                 (bad-request!
                                   {:error
                                    (str "Please provide exactly one of the following fields: "
                                         (str/join ", " (->> one-of-kws (map name) sort))
                                         "\n"
                                         (if-let [given-str (when (seq provided-types)
                                                              (->> provided-types
                                                                   keys
                                                                   (map name)
                                                                   sort
                                                                   (str/join ", ")))]
                                           (str "Provided: " given-str)
                                           "None provided."))}))
                               (-> provided-types first key))
              additional-required-capabilities
              (case source-type-kw
                :casebook_id #{:read-casebook}
                :investigation_id #{:read-investigation})
              _ (require-capability! additional-required-capabilities
                                     identity)
              incident (read-store :incident
                                   read-record
                                   id
                                   identity-map
                                   {})
              source-short-id (-> link-req
                                  source-type-kw
                                  long-id->id
                                  :short-id)
              source-store-kw (case source-type-kw
                                :casebook_id :casebook
                                :investigation_id :investigation)
              source (when source-short-id
                       (read-store source-store-kw
                                   read-record
                                   source-short-id
                                   identity-map
                                   {}))
              target-ref (short-id->long-id id
                                            get-http-show)]
          (cond
            (or (not incident)
                (not target-ref))
            (not-found {:error "Invalid Incident id"})
            (not source)
            (bad-request {:error (str "Invalid "
                                      (case source-type-kw
                                        :casebook_id "Casebook"
                                        :investigation_id "Investigation")
                                      " id")})
            :else
            (let [{source-id source-type-kw
                   :keys [tlp]
                   :or {tlp "amber"}} link-req
                  new-relationship
                  {:source_ref source-id
                   :target_ref target-ref
                   :relationship_type "related-to"
                   :tlp tlp}
                  stored-relationship
                  (-> (flows/create-flow
                       :apply-hooks apply-hooks
                       :apply-event-hooks apply-event-hooks
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

(def relationship-histogram-fields
  [:timestamp])

(def relationship-enumerable-fields
  [:source
   :relationship_type])

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
    :external-id-capabilities :read-relationship
    :can-aggregate? true
    :histogram-fields relationship-histogram-fields
    :enumerable-fields relationship-enumerable-fields}))

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
