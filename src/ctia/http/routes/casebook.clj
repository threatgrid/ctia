(ns ctia.http.routes.casebook
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.casebook
    :refer
    [page-with-long-id with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer
    [created
     filter-map-search-options
     paginated-ok
     CasebookByExternalIdQueryParams
     CasebookGetParams
     CasebookSearchParams
     search-options]]
   [ctia.schemas.core
    :refer
    [Observable
     NewCasebook
     PartialNewCasebook
     PartialCasebook
     PartialCasebookList
     Casebook
     CasebookObservablesUpdate
     CasebookTextsUpdate
     CasebookBundleUpdate]]
   [ctia.store :refer :all]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema.core :as s]))

(defroutes casebook-routes
  (context "/casebook" []
           :tags ["Casebook"]
           (POST "/" []
                 :return Casebook
                 :body [casebook NewCasebook {:description "a new Casebook"}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "Adds a new Casebook"
                 :capabilities :create-casebook
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :entity-type :casebook
                      :realize-fn ent/realize-casebook
                      :store-fn #(write-store :casebook
                                              create-casebooks
                                              %
                                              identity-map
                                              {})
                      :long-id-fn with-long-id
                      :entity-type :casebook
                      :identity identity
                      :entities [casebook]
                      :spec :new-casebook/map)
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return Casebook
                :body [casebook NewCasebook {:description "an updated Casebook"}]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "Updates an Casebook"
                :path-params [id :- s/Str]
                :capabilities :create-casebook
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow
                     :get-fn #(read-store :casebook
                                          read-casebook
                                          %
                                          identity-map
                                          {})
                     :realize-fn ent/realize-casebook
                     :update-fn #(write-store :casebook
                                              update-casebook
                                              (:id %)
                                              %
                                              identity-map)
                     :long-id-fn with-long-id
                     :entity-type :casebook
                     :entity-id id
                     :identity identity
                     :entity casebook
                     :spec :new-casebook/map)
                    ent/un-store
                    ok))

           (PATCH "/:id" []
                  :return Casebook
                  :body [partial-casebook PartialNewCasebook {:description "a Casebook partial update"}]
                  :header-params [{Authorization :- (s/maybe s/Str) nil}]
                  :summary "Partially Update a Casebook"
                  :path-params [id :- s/Str]
                  :capabilities :create-casebook
                  :identity identity
                  :identity-map identity-map
                  (-> (flows/patch-flow
                       :get-fn #(read-store :casebook
                                            read-casebook
                                            %
                                            identity-map
                                            {})
                       :realize-fn ent/realize-casebook
                       :update-fn #(write-store :casebook
                                                update-casebook
                                                (:id %)
                                                %
                                                identity-map)
                       :long-id-fn with-long-id
                       :entity-type :casebook
                       :entity-id id
                       :identity identity
                       :patch-operation :replace
                       :partial-entity partial-casebook
                       :spec :new-casebook/map)
                      ent/un-store
                      ok))

           (GET "/external_id/:external_id" []
                :return PartialCasebookList
                :query [q CasebookByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List casebooks by external id"
                :capabilities #{:read-casebook :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :casebook list-casebooks
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return PartialCasebookList
                :summary "Search for an Casebook using a Lucene/ES query string"
                :query [params CasebookSearchParams]
                :capabilities #{:read-casebook :search-casebook}
                :identity identity
                :identity-map identity-map
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                (-> (query-string-search-store
                     :casebook
                     query-string-search
                     (:query params)
                     (apply dissoc params filter-map-search-options)
                     identity-map
                     (select-keys params search-options))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe PartialCasebook)
                :summary "Gets an Casebook by ID"
                :path-params [id :- s/Str]
                :query [params CasebookGetParams]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-casebook
                :identity identity
                :identity-map identity-map
                (if-let [casebook (read-store :casebook
                                              read-casebook
                                              id
                                              identity-map
                                              params)]
                  (-> casebook
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

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
                                                    read-casebook
                                                    %
                                                    identity-map
                                                    {})
                               :realize-fn ent/realize-casebook
                               :update-fn #(write-store :casebook
                                                        update-casebook
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
                              ent/un-store
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
                                                    read-casebook
                                                    %
                                                    identity-map
                                                    {})
                               :realize-fn ent/realize-casebook
                               :update-fn #(write-store :casebook
                                                        update-casebook
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
                              ent/un-store
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
                                                    read-casebook
                                                    %
                                                    identity-map
                                                    {})
                               :realize-fn ent/realize-casebook
                               :update-fn #(write-store :casebook
                                                        update-casebook
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
                              ent/un-store
                              ok)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes an Casebook"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-casebook
                   :identity identity
                   :identity-map identity-map
                   (if (flows/delete-flow
                        :get-fn #(read-store :casebook
                                             read-casebook
                                             %
                                             identity-map
                                             {})
                        :delete-fn #(write-store :casebook
                                                 delete-casebook
                                                 %
                                                 identity-map)
                        :entity-type :casebook
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
