(ns ctia.http.routes.relationship
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.relationship :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created
            paginated-ok
            filter-map-search-options
            search-options
            PagingParams
            RelationshipGetParams
            RelationshipSearchParams
            RelationshipByExternalIdQueryParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core
    :refer [NewRelationship Relationship PartialRelationship PartialRelationshipList]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

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
                                              identity-map
                                              {})
                      :long-id-fn with-long-id
                      :entity-type :relationship
                      :identity identity
                      :entities [relationship]
                      :spec :new-relationship/map)
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return Relationship
                :body [relationship NewRelationship {:description "an updated Relationship"}]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "Updates a Relationship"
                :path-params [id :- s/Str]
                :capabilities :create-relationship
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow
                     :get-fn #(read-store :relationship
                                          read-relationship
                                          %
                                          identity-map
                                          {})
                     :realize-fn ent/realize-relationship
                     :update-fn #(write-store :relationship
                                              update-relationship
                                              (:id %)
                                              %
                                              identity-map)
                     :long-id-fn with-long-id
                     :entity-type :relationship
                     :entity-id id
                     :identity identity
                     :entity relationship
                     :spec :new-relationship/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return PartialRelationshipList
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
                :return PartialRelationshipList
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
                     (apply dissoc params filter-map-search-options)
                     identity-map
                     (select-keys params search-options))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe PartialRelationship)
                :summary "Gets an Relationship by ID"
                :path-params [id :- s/Str]
                :query [params RelationshipGetParams]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-relationship
                :identity identity
                :identity-map identity-map
                (if-let [relationship (read-store :relationship
                                                  read-relationship
                                                  id
                                                  identity-map
                                                  params)]
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
                                             identity-map
                                             {})
                        :delete-fn #(write-store :relationship
                                                 delete-relationship
                                                 %
                                                 identity-map)
                        :entity-type :relationship
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
