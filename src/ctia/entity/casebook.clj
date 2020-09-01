(ns ctia.entity.casebook
  (:require [compojure.api.sweet :refer [context POST PATCH routes]]
            [ctia.domain.entities :refer [default-realize-fn un-store with-long-id]]
            [ctia.flows.crud :as flows]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams
                             PagingParams
                             SourcableEntityFilterParams
                             wait_for->refresh]]
             [crud :refer [services->entity-crud-routes]]]
            [ctia.schemas
             [utils :as csu]
             [core :refer [APIHandlerServices Bundle def-acl-schema def-stored-schema]]
             [sorting :as sorting]]
            [ctia.schemas.graphql
             [flanders :as flanders]
             [helpers :as g]
             [pagination :as pagination]
             [sorting :as graphql-sorting]
             [refs :as refs]]
            [ctia.store :refer [read-record
                                update-record]]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [ctim.schemas.casebook :as cs]
            [flanders.utils :as fu]
            [ring.swagger.schema :refer [describe]]
            [ring.util.http-response :refer [ok not-found]]
            [schema-tools.core :as st]
            [schema.core :as s]
            [ctia.schemas.graphql.ownership :as go]))

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

(def-stored-schema StoredCasebook Casebook)

(s/defschema PartialStoredCasebook
  (csu/optional-keys-schema StoredCasebook))

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
   (st/optional-keys
    {:query s/Str
     :texts.text s/Str
     :sort_by casebook-sort-fields})))

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

(s/defn casebook-operation-routes [{{:keys [get-in-config]} :ConfigService
                                    {:keys [read-store write-store]} :StoreService
                                    :as _services_} :- APIHandlerServices]
  (routes
   (PATCH "/:id" []
          :return Casebook
          :body [partial-casebook PartialNewCasebook {:description "a Casebook partial update"}]
          :summary "Partially Update a Casebook"
          :query-params [{wait_for :- (describe s/Bool "wait for patched entity to be available for search") nil}]
          :path-params [id :- s/Str]
          :capabilities :create-casebook
          :auth-identity identity
          :identity-map identity-map
          (if-let [res (flows/patch-flow
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
                                                 identity-map
                                                 (wait_for->refresh wait_for))
                        :long-id-fn #(with-long-id % get-in-config)
                        :entity-type :casebook
                        :entity-id id
                        :identity identity
                        :patch-operation :replace
                        :partial-entity partial-casebook
                        :spec :new-casebook/map)]
            (ok (un-store res))
            (not-found {:error "casebook not found"})))
   (context "/:id/observables" []
            (POST "/" []
                  :return Casebook
                  :body [operation CasebookObservablesUpdate
                         {:description "A casebook Observables operation"}]
                  :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
                  :path-params [id :- s/Str]
                  :summary "Edit Observables on a casebook"
                  :capabilities :create-casebook
                  :auth-identity identity
                  :identity-map identity-map
                  (if-let [res (flows/patch-flow
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
                                                         identity-map
                                                         (wait_for->refresh wait_for))
                                :long-id-fn #(with-long-id % get-in-config)
                                :entity-type :casebook
                                :entity-id id
                                :identity identity
                                :patch-operation (:operation operation)
                                :partial-entity {:observables (:observables operation)}
                                :spec :new-casebook/map)]
                    (ok (un-store res))
                    (not-found {:error "casebook not found"}))))

   (context "/:id/texts" []
            (POST "/" []
                  :return Casebook
                  :body [operation CasebookTextsUpdate
                         {:description "A casebook Texts operation"}]
                  :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
                  :path-params [id :- s/Str]
                  :summary "Edit Texts on a casebook"
                  :capabilities :create-casebook
                  :auth-identity identity
                  :identity-map identity-map
                  (if-let [res (flows/patch-flow
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
                                                         identity-map
                                                         (wait_for->refresh wait_for))
                                :long-id-fn #(with-long-id % get-in-config)
                                :entity-type :casebook
                                :entity-id id
                                :identity identity
                                :patch-operation (:operation operation)
                                :partial-entity {:texts (:texts operation)}
                                :spec :new-casebook/map)]
                    (ok (un-store res))
                    (not-found {:error "casebook not found"}))))

   (context "/:id/bundle" []
            (POST "/" []
                  :return Casebook
                  :body [operation CasebookBundleUpdate
                         {:description "A casebook Bundle operation"}]
                  :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
                  :path-params [id :- s/Str]
                  :summary "Edit a Bundle on a casebook"
                  :capabilities :create-casebook
                  :auth-identity identity
                  :identity-map identity-map
                  (if-let [res (flows/patch-flow
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
                                                         identity-map
                                                         (wait_for->refresh wait_for))
                                :long-id-fn #(with-long-id % get-in-config)
                                :entity-type :casebook
                                :entity-id id
                                :identity identity
                                :patch-operation (:operation operation)
                                :partial-entity {:bundle (:bundle operation)}
                                :spec :new-casebook/map)]
                    (ok (un-store res))
                    (not-found {:error "casebook not found"}))))))

(def CasebookType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all cs/Casebook)
         {refs/observable-type-name refs/ObservableTypeRef
          refs/incident-type-name refs/IncidentRef
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
     (merge
      fields
      go/graphql-ownership-fields))))

(def casebook-order-arg
  (graphql-sorting/order-by-arg
   "CasebookOrder"
   "casebooks"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              casebook-fields))))

(def CasebookConnectionType
  (pagination/new-connection CasebookType))

(def casebook-enumerable-fields
  [:source
   :observables.type
   :observables.value])

(def casebook-histogram-fields
  [:timestamp])

(s/defn casebook-routes [services :- APIHandlerServices]
  (routes
   (casebook-operation-routes services)
   (services->entity-crud-routes
    services
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
     :can-aggregate? true
     :get-capabilities :read-casebook
     :post-capabilities :create-casebook
     :put-capabilities :create-casebook
     :delete-capabilities :delete-casebook
     :search-capabilities :search-casebook
     :external-id-capabilities :read-casebook
     :hide-delete? false
     :histogram-fields casebook-histogram-fields
     :enumerable-fields casebook-enumerable-fields})))

(def casebook-entity
  {:route-context "/casebook"
   :tags ["Casebook"]
   :entity :casebook
   :plural :casebooks
   :new-spec :new-casebook/map
   :schema Casebook
   :partial-schema PartialCasebook
   :partial-list-schema PartialCasebookList
   :new-schema NewCasebook
   :stored-schema StoredCasebook
   :partial-stored-schema PartialStoredCasebook
   :realize-fn realize-casebook
   :es-store ->CasebookStore
   :es-mapping casebook-mapping
   :services->routes casebook-routes
   :capabilities capabilities})
