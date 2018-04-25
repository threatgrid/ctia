(ns ctia.entity.casebook
  (:require [compojure.api.sweet :refer [context POST routes]]
            [ctia.domain.entities :refer [default-realize-fn un-store with-long-id]]
            [ctia.flows.crud :as flows]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [Bundle def-acl-schema def-stored-schema]]
             [sorting :as sorting]]
            [ctia.schemas.graphql
             [flanders :as flanders]
             [helpers :as g]
             [pagination :as pagination]
             [sorting :as graphql-sorting]
             [refs :as refs]]
            [ctia.store :refer :all]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [ctim.schemas.casebook :as cs]
            [flanders.utils :as fu]
            [ring.util.http-response :refer [ok]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def-acl-schema Casebook
  (fu/replace-either-with-any
   cs/Casebook)
  "casebook")

(def-acl-schema PartialCasebook
  (fu/replace-either-with-any
   (fu/optionalize-all cs/Casebook))
  "partial-casebook")

(s/defschema PartialCasebookList
  [PartialCasebook])

(def-acl-schema NewCasebook
  (fu/replace-either-with-any
   cs/NewCasebook)
  "new-casebook")

(def-acl-schema PartialNewCasebook
  (fu/replace-either-with-any
   (fu/optionalize-all cs/NewCasebook))
  "new-casebook")

(def-stored-schema StoredCasebook
  (fu/replace-either-with-any
   cs/StoredCasebook)
  "stored-casebook")

(def-stored-schema PartialStoredCasebook
  (fu/replace-either-with-any
   (fu/optionalize-all cs/StoredCasebook))
  "partial-stored-casebook")

(def realize-casebook
  (default-realize-fn "casebook" NewCasebook StoredCasebook))

(def casebook-mapping
  {"casebook"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:observables em/observable
      :bundle {:enabled false}
      :texts em/texts})}})

(def-es-store CasebookStore :casebook StoredCasebook PartialStoredCasebook)

(s/defschema CasebookObservablesUpdate
  (st/merge
   {:operation (s/enum :add :remove :replace)}
   (st/select-keys Casebook [:observables])))

(s/defschema CasebookTextsUpdate
  (st/merge
   {:operation (s/enum :add :remove :replace)}
   (st/select-keys Casebook [:texts])))

(s/defschema CasebookBundleUpdate
  {:operation (s/enum :add :remove :replace)
   :bundle (st/optional-keys Bundle)})

(def casebook-fields
  (concat sorting/default-entity-sort-fields
          sorting/describable-entity-sort-fields
          sorting/sourcable-entity-sort-fields))

(def casebook-sort-fields
  (apply s/enum casebook-fields))

(s/defschema CasebookFieldsParam
  {(s/optional-key :fields) [casebook-sort-fields]})

(s/defschema CasebookSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   CasebookFieldsParam
   {:query s/Str
    (s/optional-key :texts.text) s/Str
    (s/optional-key :sort_by) casebook-sort-fields}))

(def CasebookGetParams CasebookFieldsParam)


(s/defschema CasebookByExternalIdQueryParams
  (st/merge
   PagingParams
   CasebookFieldsParam))

(def capabilities
  #{:create-casebook
    :read-casebook
    :list-casebooks
    :delete-casebook
    :search-casebook})

(def casebook-operation-routes
  (routes
   (context "/:id/observables" []
            (POST "/" []
                  :return Casebook
                  :body [operation CasebookObservablesUpdate
                         {:description "A casebook Observables operation"}]
                  :path-params [id :- s/Str]
                  :header-params [{Authorization :- (s/maybe s/Str) nil}]
                  :summary "Edit Observables on a casebook"
                  :capabilities :create-casebook
                  :auth-identity identity
                  :identity-map identity-map
                  (-> (flows/patch-flow
                       :get-fn #(read-store :casebook
                                            read-record
                                            %
                                            identity-map
                                            {})
                       :realize-fn realize-casebook
                       :update-fn #(write-store :casebook
                                                update-record
                                                (:id %)
                                                %
                                                identity-map)
                       :long-id-fn with-long-id
                       :entity-type :casebook
                       :entity-id id
                       :identity identity
                       :patch-operation (:operation operation)
                       :partial-entity {:observables (:observables operation)}
                       :spec :new-casebook/map)
                      un-store
                      ok)))

   (context "/:id/texts" []
            (POST "/" []
                  :return Casebook
                  :body [operation CasebookTextsUpdate
                         {:description "A casebook Texts operation"}]
                  :path-params [id :- s/Str]
                  :header-params [{Authorization :- (s/maybe s/Str) nil}]
                  :summary "Edit Texts on a casebook"
                  :capabilities :create-casebook
                  :auth-identity identity
                  :identity-map identity-map
                  (-> (flows/patch-flow
                       :get-fn #(read-store :casebook
                                            read-record
                                            %
                                            identity-map
                                            {})
                       :realize-fn realize-casebook
                       :update-fn #(write-store :casebook
                                                update-record
                                                (:id %)
                                                %
                                                identity-map)
                       :long-id-fn with-long-id
                       :entity-type :casebook
                       :entity-id id
                       :identity identity
                       :patch-operation (:operation operation)
                       :partial-entity {:texts (:texts operation)}
                       :spec :new-casebook/map)
                      un-store
                      ok)))

   (context "/:id/bundle" []
            (POST "/" []
                  :return Casebook
                  :body [operation CasebookBundleUpdate
                         {:description "A casebook Bundle operation"}]
                  :path-params [id :- s/Str]
                  :header-params [{Authorization :- (s/maybe s/Str) nil}]
                  :summary "Edit a Bundle on a casebook"
                  :capabilities :create-casebook
                  :auth-identity identity
                  :identity-map identity-map
                  (-> (flows/patch-flow
                       :get-fn #(read-store :casebook
                                            read-record
                                            %
                                            identity-map
                                            {})
                       :realize-fn realize-casebook
                       :update-fn #(write-store :casebook
                                                update-record
                                                (:id %)
                                                %
                                                identity-map)
                       :long-id-fn with-long-id
                       :entity-type :casebook
                       :entity-id id
                       :identity identity
                       :patch-operation (:operation operation)
                       :partial-entity {:bundle (:bundle operation)}
                       :spec :new-casebook/map)
                      un-store
                      ok)))))

(def CasebookType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all cs/Casebook)
         {refs/observable-type-name refs/ObservableTypeRef
          refs/judgement-type-name refs/JudgementRef
          refs/sighting-type-name refs/SightingRef
          refs/verdict-type-name refs/VerdictRef
          refs/attack-pattern-type-name refs/AttackPatternRef
          refs/malware-type-name refs/MalwareRef
          refs/tool-type-name refs/ToolRef})]
    (g/new-object
     name
     description
     []
     fields)))

(def casebook-order-arg
  (graphql-sorting/order-by-arg
   "CasebookOrder"
   "casebooks"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              casebook-fields))))

(def CasebookConnectionType
  (pagination/new-connection CasebookType))

(def casebook-routes
  (entity-crud-routes
   {:api-tags ["Casebook"]
    :entity :casebook
    :new-schema NewCasebook
    :entity-schema Casebook
    :get-schema PartialCasebook
    :get-params CasebookGetParams
    :list-schema PartialCasebookList
    :search-schema PartialCasebookList
    :external-id-q-params CasebookByExternalIdQueryParams
    :search-q-params CasebookSearchParams
    :new-spec :new-casebook/map
    :realize-fn realize-casebook
    :get-capabilities :read-casebook
    :post-capabilities :create-casebook
    :put-capabilities :create-casebook
    :delete-capabilities :delete-casebook
    :search-capabilities :search-casebook
    :external-id-capabilities #{:read-casebook :external-id}
    :hide-delete? false}))

(def casebook-entity
  {:route-context "/casebook"
   :tags ["Casebook"]
   :entity :casebook
   :plural :casebooks
   :schema Casebook
   :partial-schema PartialCasebook
   :partial-list-schema PartialCasebookList
   :new-schema NewCasebook
   :stored-schema StoredCasebook
   :partial-stored-schema PartialStoredCasebook
   :realize-fn realize-casebook
   :es-store ->CasebookStore
   :es-mapping casebook-mapping
   :routes casebook-routes
   :capabilities capabilities})
