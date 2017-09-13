(ns ctia.http.routes.actor
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.actor :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created paginated-ok PagingParams ActorSearchParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewActor Actor]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema ActorByExternalIdQueryParams
  PagingParams)

(defroutes actor-routes
  (context "/actor" []
           :tags ["Actor"]
           (POST "/" []
                 :return Actor
                 :body [actor NewActor {:description "a new Actor"}]
                 :header-params [api_key :- (s/maybe s/Str)]
                 :summary "Adds a new Actor"
                 :capabilities :create-actor
                 :identity identity
                 (-> (flows/create-flow
                      :entity-type :actor
                      :realize-fn ent/realize-actor
                      :store-fn #(write-store :actor create-actors %)
                      :long-id-fn with-long-id
                      :entity-type :actor
                      :identity identity
                      :entities [actor]
                      :spec :new-actor/map)
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return Actor
                :body [actor NewActor {:description "an updated Actor"}]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "Updates an Actor"
                :path-params [id :- s/Str]
                :capabilities :create-actor
                :identity identity
                (-> (flows/update-flow
                     :get-fn #(read-store :actor read-actor %)
                     :realize-fn ent/realize-actor
                     :update-fn #(write-store :actor update-actor (:id %) %)
                     :long-id-fn with-long-id
                     :entity-type :actor
                     :entity-id id
                     :identity identity
                     :entity actor
                     :spec :new-actor/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return (s/maybe [Actor])
                :query [q ActorByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "List actors by external id"
                :capabilities #{:read-actor :external-id}
                (-> (read-store :actor list-actors
                                {:external_ids external_id} q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return (s/maybe [Actor])
                :summary "Search for an Actor using a Lucene/ES query string"
                :query [params ActorSearchParams]
                :capabilities #{:read-actor :search-actor}
                :header-params [api_key :- (s/maybe s/Str)]
                (-> (query-string-search-store
                     :actor
                     query-string-search
                     (:query params)
                     (dissoc params :query :sort_by :sort_order :offset :limit)
                     (select-keys params [:sort_by :sort_order :offset :limit]))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe Actor)
                :summary "Gets an Actor by ID"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :read-actor
                (if-let [actor (read-store :actor read-actor id)]
                  (-> actor
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes an Actor"
                   :header-params [api_key :- (s/maybe s/Str)]
                   :capabilities :delete-actor
                   :identity identity
                   (if (flows/delete-flow
                        :get-fn #(read-store :actor read-actor %)
                        :delete-fn #(write-store :actor delete-actor %)
                        :entity-type :actor
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
