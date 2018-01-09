(ns ctia.http.routes.actor
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.actor :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created
            search-options
            filter-map-search-options
            paginated-ok
            PagingParams
            ActorGetParams
            ActorSearchParams
            ActorByExternalIdQueryParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core
    :refer [NewActor Actor PartialActor PartialActorList]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(defroutes actor-routes
  (context "/actor" []
           :tags ["Actor"]
           (POST "/" []
                 :return Actor
                 :body [actor NewActor {:description "a new Actor"}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "Adds a new Actor"
                 :capabilities :create-actor
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :entity-type :actor
                      :realize-fn ent/realize-actor
                      :store-fn #(write-store :actor
                                              create-actors
                                              %
                                              identity-map)
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
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "Updates an Actor"
                :path-params [id :- s/Str]
                :capabilities :create-actor
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow
                     :get-fn #(read-store :actor
                                          read-actor
                                          %
                                          identity-map
                                          {})
                     :realize-fn ent/realize-actor
                     :update-fn #(write-store :actor
                                              update-actor
                                              (:id %)
                                              %
                                              identity-map)
                     :long-id-fn with-long-id
                     :entity-type :actor
                     :entity-id id
                     :identity identity
                     :entity actor
                     :spec :new-actor/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return PartialActorList
                :query [q ActorByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List actors by external id"
                :capabilities #{:read-actor :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :actor list-actors
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return PartialActorList
                :summary "Search for an Actor using a Lucene/ES query string"
                :query [params ActorSearchParams]
                :capabilities #{:read-actor :search-actor}
                :identity identity
                :identity-map identity-map
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                (-> (query-string-search-store
                     :actor
                     query-string-search
                     (:query params)
                     (apply dissoc params filter-map-search-options)
                     identity-map
                     (select-keys params search-options))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe PartialActor)
                :summary "Gets an Actor by ID"
                :path-params [id :- s/Str]
                :query [params ActorGetParams]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-actor
                :identity identity
                :identity-map identity-map
                (if-let [actor (read-store :actor
                                           read-actor
                                           id
                                           identity-map
                                           params)]
                  (-> actor
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes an Actor"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-actor
                   :identity identity
                   :identity-map identity-map
                   (if (flows/delete-flow
                        :get-fn #(read-store :actor
                                             read-actor
                                             %
                                             identity-map
                                             {})
                        :delete-fn #(write-store :actor
                                                 delete-actor
                                                 %
                                                 identity-map)
                        :entity-type :actor
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
