(ns ctia.http.routes.attack-pattern
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.attack-pattern :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created
            paginated-ok
            filter-map-search-options
            search-options
            PagingParams
            AttackPatternGetParams
            AttackPatternSearchParams
            AttackPatternByExternalIdQueryParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core
    :refer [NewAttackPattern AttackPattern PartialAttackPattern PartialAttackPatternList]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(defroutes attack-pattern-routes
  (context "/attack-pattern" []
           :tags ["Attack Pattern"]
           (POST "/" []
                 :return AttackPattern
                 :body [attack-pattern NewAttackPattern {:description "a new Attack Pattern"}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "Adds a new Attack Pattern"
                 :capabilities :create-attack-pattern
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :entity-type :attack-pattern
                      :realize-fn ent/realize-attack-pattern
                      :store-fn #(write-store :attack-pattern
                                              create-attack-patterns
                                              %
                                              identity-map
                                              {})
                      :long-id-fn with-long-id
                      :entity-type :attack-pattern
                      :identity identity
                      :entities [attack-pattern]
                      :spec :new-attack-pattern/map)
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return AttackPattern
                :body [attack-pattern NewAttackPattern {:description "an updated Attack-Pattern"}]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "Updates an Attack Pattern"
                :path-params [id :- s/Str]
                :capabilities :create-attack-pattern
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow
                     :get-fn #(read-store :attack-pattern
                                          read-attack-pattern
                                          %
                                          identity-map
                                          {})
                     :realize-fn ent/realize-attack-pattern
                     :update-fn #(write-store :attack-pattern
                                              update-attack-pattern
                                              (:id %)
                                              %
                                              identity-map)
                     :long-id-fn with-long-id
                     :entity-type :attack-pattern
                     :entity-id id
                     :identity identity
                     :entity attack-pattern
                     :spec :new-attack-pattern/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return PartialAttackPatternList
                :query [q AttackPatternByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List attack patterns by external id"
                :capabilities #{:read-attack-pattern :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :attack-pattern list-attack-patterns
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return PartialAttackPatternList
                :summary "Search for an Attack Pattern using a Lucene/ES query string"
                :query [params AttackPatternSearchParams]
                :capabilities #{:read-attack-pattern :search-attack-pattern}
                :identity identity
                :identity-map identity-map
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                (-> (query-string-search-store
                     :attack-pattern
                     query-string-search
                     (:query params)
                     (apply dissoc params filter-map-search-options)
                     identity-map
                     (select-keys params search-options))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe PartialAttackPattern)
                :summary "Gets an Attack Pattern by ID"
                :path-params [id :- s/Str]
                :query [params AttackPatternGetParams]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-attack-pattern
                :identity identity
                :identity-map identity-map
                (if-let [attack-pattern (read-store :attack-pattern
                                                    read-attack-pattern
                                                    id
                                                    identity-map
                                                    params)]
                  (-> attack-pattern
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes an Attack Pattern"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-attack-pattern
                   :identity identity
                   :identity-map identity-map
                   (if (flows/delete-flow
                        :get-fn #(read-store :attack-pattern
                                             read-attack-pattern
                                             %
                                             identity-map
                                             {})
                        :delete-fn #(write-store :attack-pattern
                                                 delete-attack-pattern
                                                 %
                                                 identity-map)
                        :entity-type :attack-pattern
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
