(ns ctia.casebook.routes
  (:refer-clojure :exclude [read update identity])
  (:require
   [ctia.store :refer [read update delete]]
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities
    :refer [realize-casebook
            un-store
            with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes
    [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
    [crud :refer [entity-crud-routes]]]
   [ctia.schemas
    [core :refer [Casebook CasebookBundleUpdate CasebookObservablesUpdate CasebookTextsUpdate NewCasebook PartialCasebook PartialCasebookList]]
    [sorting :as sorting]]
   [ctia.store :refer [write-store read-store]]
   [ring.util.http-response :refer [ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def casebook-sort-fields
  (apply s/enum sorting/casebook-sort-fields))

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
                  :identity identity
                  :identity-map identity-map
                  (-> (flows/patch-flow
                       :get-fn #(read-store :casebook
                                            read
                                            %
                                            identity-map
                                            {})
                       :realize-fn realize-casebook
                       :update-fn #(write-store :casebook
                                                update
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
                  :identity identity
                  :identity-map identity-map
                  (-> (flows/patch-flow
                       :get-fn #(read-store :casebook
                                            read
                                            %
                                            identity-map
                                            {})
                       :realize-fn realize-casebook
                       :update-fn #(write-store :casebook
                                                update
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
                  :identity identity
                  :identity-map identity-map
                  (-> (flows/patch-flow
                       :get-fn #(read-store :casebook
                                            read
                                            %
                                            identity-map
                                            {})
                       :realize-fn realize-casebook
                       :update-fn #(write-store :casebook
                                                update
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
