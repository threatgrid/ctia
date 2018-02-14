(ns ctia.http.routes.scratchpad
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.scratchpad
    :refer
    [page-with-long-id with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer
    [created
     filter-map-search-options
     paginated-ok
     ScratchpadByExternalIdQueryParams
     ScratchpadGetParams
     ScratchpadSearchParams
     search-options]]
   [ctia.schemas.core
    :refer
    [Observable
     NewScratchpad
     PartialNewScratchpad
     PartialScratchpad
     PartialScratchpadList
     Scratchpad
     ScratchpadObservablesUpdate
     ScratchpadTextsUpdate
     ScratchpadBundleUpdate]]
   [ctia.store :refer :all]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema.core :as s]))

(defroutes scratchpad-routes
  (context "/scratchpad" []
           :tags ["Scratchpad"]
           (POST "/" []
                 :return Scratchpad
                 :body [scratchpad NewScratchpad {:description "a new Scratchpad"}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "Adds a new Scratchpad"
                 :capabilities :create-scratchpad
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :entity-type :scratchpad
                      :realize-fn ent/realize-scratchpad
                      :store-fn #(write-store :scratchpad
                                              create-scratchpads
                                              %
                                              identity-map
                                              {})
                      :long-id-fn with-long-id
                      :entity-type :scratchpad
                      :identity identity
                      :entities [scratchpad]
                      :spec :new-scratchpad/map)
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return Scratchpad
                :body [scratchpad NewScratchpad {:description "an updated Scratchpad"}]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "Updates an Scratchpad"
                :path-params [id :- s/Str]
                :capabilities :create-scratchpad
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow
                     :get-fn #(read-store :scratchpad
                                          read-scratchpad
                                          %
                                          identity-map
                                          {})
                     :realize-fn ent/realize-scratchpad
                     :update-fn #(write-store :scratchpad
                                              update-scratchpad
                                              (:id %)
                                              %
                                              identity-map)
                     :long-id-fn with-long-id
                     :entity-type :scratchpad
                     :entity-id id
                     :identity identity
                     :entity scratchpad
                     :spec :new-scratchpad/map)
                    ent/un-store
                    ok))

           (PATCH "/:id" []
                  :return Scratchpad
                  :body [partial-scratchpad PartialNewScratchpad {:description "a Scratchpad partial update"}]
                  :header-params [{Authorization :- (s/maybe s/Str) nil}]
                  :summary "Partially Update a Scratchpad"
                  :path-params [id :- s/Str]
                  :capabilities :create-scratchpad
                  :identity identity
                  :identity-map identity-map
                  (-> (flows/patch-flow
                       :get-fn #(read-store :scratchpad
                                            read-scratchpad
                                            %
                                            identity-map
                                            {})
                       :realize-fn ent/realize-scratchpad
                       :update-fn #(write-store :scratchpad
                                                update-scratchpad
                                                (:id %)
                                                %
                                                identity-map)
                       :long-id-fn with-long-id
                       :entity-type :scratchpad
                       :entity-id id
                       :identity identity
                       :patch-operation :replace
                       :partial-entity partial-scratchpad
                       :spec :new-scratchpad/map)
                      ent/un-store
                      ok))

           (GET "/external_id/:external_id" []
                :return PartialScratchpadList
                :query [q ScratchpadByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List scratchpads by external id"
                :capabilities #{:read-scratchpad :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :scratchpad list-scratchpads
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return PartialScratchpadList
                :summary "Search for an Scratchpad using a Lucene/ES query string"
                :query [params ScratchpadSearchParams]
                :capabilities #{:read-scratchpad :search-scratchpad}
                :identity identity
                :identity-map identity-map
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                (-> (query-string-search-store
                     :scratchpad
                     query-string-search
                     (:query params)
                     (apply dissoc params filter-map-search-options)
                     identity-map
                     (select-keys params search-options))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe PartialScratchpad)
                :summary "Gets an Scratchpad by ID"
                :path-params [id :- s/Str]
                :query [params ScratchpadGetParams]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-scratchpad
                :identity identity
                :identity-map identity-map
                (if-let [scratchpad (read-store :scratchpad
                                                read-scratchpad
                                                id
                                                identity-map
                                                params)]
                  (-> scratchpad
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (context "/:id/observables" []
                    (POST "/" []
                          :return Scratchpad
                          :body [operation ScratchpadObservablesUpdate
                                 {:description "A scratchpad Observables operation"}]
                          :path-params [id :- s/Str]
                          :header-params [{Authorization :- (s/maybe s/Str) nil}]
                          :summary "Edit Observables on a scratchpad"
                          :capabilities :create-scratchpad
                          :identity identity
                          :identity-map identity-map
                          (-> (flows/patch-flow
                               :get-fn #(read-store :scratchpad
                                                    read-scratchpad
                                                    %
                                                    identity-map
                                                    {})
                               :realize-fn ent/realize-scratchpad
                               :update-fn #(write-store :scratchpad
                                                        update-scratchpad
                                                        (:id %)
                                                        %
                                                        identity-map)
                               :long-id-fn with-long-id
                               :entity-type :scratchpad
                               :entity-id id
                               :identity identity
                               :patch-operation (:operation operation)
                               :partial-entity {:observables (:observables operation)}
                               :spec :new-scratchpad/map)
                              ent/un-store
                              ok)))

           (context "/:id/texts" []
                    (POST "/" []
                          :return Scratchpad
                          :body [operation ScratchpadTextsUpdate
                                 {:description "A scratchpad Texts operation"}]
                          :path-params [id :- s/Str]
                          :header-params [{Authorization :- (s/maybe s/Str) nil}]
                          :summary "Edit Texts on a scratchpad"
                          :capabilities :create-scratchpad
                          :identity identity
                          :identity-map identity-map
                          (-> (flows/patch-flow
                               :get-fn #(read-store :scratchpad
                                                    read-scratchpad
                                                    %
                                                    identity-map
                                                    {})
                               :realize-fn ent/realize-scratchpad
                               :update-fn #(write-store :scratchpad
                                                        update-scratchpad
                                                        (:id %)
                                                        %
                                                        identity-map)
                               :long-id-fn with-long-id
                               :entity-type :scratchpad
                               :entity-id id
                               :identity identity
                               :patch-operation (:operation operation)
                               :partial-entity {:texts (:texts operation)}
                               :spec :new-scratchpad/map)
                              ent/un-store
                              ok)))

           (context "/:id/bundle" []
                    (POST "/" []
                          :return Scratchpad
                          :body [operation ScratchpadBundleUpdate
                                 {:description "A scratchpad Bundle operation"}]
                          :path-params [id :- s/Str]
                          :header-params [{Authorization :- (s/maybe s/Str) nil}]
                          :summary "Edit a Bundle on a scratchpad"
                          :capabilities :create-scratchpad
                          :identity identity
                          :identity-map identity-map
                          (-> (flows/patch-flow
                               :get-fn #(read-store :scratchpad
                                                    read-scratchpad
                                                    %
                                                    identity-map
                                                    {})
                               :realize-fn ent/realize-scratchpad
                               :update-fn #(write-store :scratchpad
                                                        update-scratchpad
                                                        (:id %)
                                                        %
                                                        identity-map)
                               :long-id-fn with-long-id
                               :entity-type :scratchpad
                               :entity-id id
                               :identity identity
                               :patch-operation (:operation operation)
                               :partial-entity {:bundle (:bundle operation)}
                               :spec :new-scratchpad/map)
                              ent/un-store
                              ok)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes an Scratchpad"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-scratchpad
                   :identity identity
                   :identity-map identity-map
                   (if (flows/delete-flow
                        :get-fn #(read-store :scratchpad
                                             read-scratchpad
                                             %
                                             identity-map
                                             {})
                        :delete-fn #(write-store :scratchpad
                                                 delete-scratchpad
                                                 %
                                                 identity-map)
                        :entity-type :scratchpad
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
