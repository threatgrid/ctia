(ns ctia.http.routes.relationship
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :refer [realize-relationship]]
   [ctia.domain.entities.relationship :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :refer [created paginated-ok PagingParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewRelationship StoredRelationship]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema RelationshipByExternalIdQueryParams
  (st/merge
   PagingParams
   {:external_id s/Str}))

(defroutes relationship-routes
  (context "/relationship" []
           :tags ["Relationship"]
           (POST "/" []
                 :return StoredRelationship
                 :body [relationship NewRelationship
                        {:description "a new Relationship"}]
                 :header-params [api_key :- (s/maybe s/Str)]
                 :summary "Adds a new Relationship"
                 :capabilities :create-relationship
                 :identity identity
                 (created
                  (with-long-id
                    (first
                     (flows/create-flow
                      :entity-type :relationship
                      :realize-fn realize-relationship
                      :store-fn #(write-store :relationship create-relationships %)
                      :entity-type :relationship
                      :identity identity
                      :entities [relationship])))))
           (GET "/external_id" []
                :return [(s/maybe StoredRelationship)]
                :query [q RelationshipByExternalIdQueryParams]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "List relationships by external id"
                :capabilities #{:read-relationship :external-id}
                (paginated-ok
                 (page-with-long-id
                  (read-store :relationship list-relationships
                              {:external_ids (:external_id q)} q))))

           (GET "/:id" []
                :return (s/maybe StoredRelationship)
                :summary "Gets an Relationship by ID"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :read-relationship
                (if-let [d (read-store :relationship read-relationship id)]
                  (ok (with-long-id d))
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes an Relationship"
                   :header-params [api_key :- (s/maybe s/Str)]
                   :capabilities :delete-relationship
                   :identity identity
                   (if (flows/delete-flow
                        :get-fn #(read-store :relationship read-relationship %)
                        :delete-fn #(write-store :relationship delete-relationship %)
                        :entity-type :relationship
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
