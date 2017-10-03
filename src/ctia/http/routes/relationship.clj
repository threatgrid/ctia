(ns ctia.http.routes.relationship
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.relationship :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created
            paginated-ok
            PagingParams
            RelationshipSearchParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewRelationship Relationship]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema RelationshipByExternalIdQueryParams
  PagingParams)

(defroutes relationship-routes
  (context "/relationship" []
           :tags ["Relationship"]
           (POST "/" []
                 :return Relationship
                 :body [relationship NewRelationship
                        {:description "a new Relationship"}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "Adds a new Relationship"
                 :capabilities :create-relationship
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :entity-type :relationship
                      :realize-fn ent/realize-relationship
                      :store-fn #(write-store :relationship
                                              create-relationships
                                              %
                                              identity-map)
                      :long-id-fn with-long-id
                      :entity-type :relationship
                      :identity identity
                      :entities [relationship]
                      :spec :new-relationship/map)
                     first
                     ent/un-store
                     created))

           (GET "/external_id/:external_id" []
                :return [(s/maybe Relationship)]
                :query [q RelationshipByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List relationships by external id"
                :capabilities #{:read-relationship :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :relationship
                                list-relationships
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return (s/maybe [Relationship])
                :summary "Search for a Relationship using a Lucene/ES query string"
                :query [params RelationshipSearchParams]
                :capabilities #{:read-relationship :search-relationship}
                :identity identity
                :identity-map identity-map
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                (-> (query-string-search-store
                     :relationship
                     query-string-search
                     (:query params)
                     (dissoc params :query :sort_by :sort_order :offset :limit)
                     identity-map
                     (select-keys params [:sort_by :sort_order :offset :limit]))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe Relationship)
                :summary "Gets an Relationship by ID"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-relationship
                :identity identity
                :identity-map identity-map
                (if-let [relationship (read-store :relationship
                                                  read-relationship
                                                  id
                                                  identity-map)]
                  (-> relationship
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes an Relationship"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-relationship
                   :identity identity
                   :identity-map identity-map
                   (if (flows/delete-flow
                        :get-fn #(read-store :relationship
                                             read-relationship
                                             %
                                             identity-map)
                        :delete-fn #(write-store :relationship
                                                 delete-relationship
                                                 %
                                                 identity-map)
                        :entity-type :relationship
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
