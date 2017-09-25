(ns ctia.http.routes.coa
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities :as ent]
            [ctia.domain.entities.coa :refer [with-long-id page-with-long-id]]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.http.routes.common
             :refer [created
                     paginated-ok
                     PagingParams
                     COASearchParams]]
            [ctia.schemas.core :refer [NewCOA COA]]
            [ring.util.http-response :refer [ok no-content not-found]]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema COAByExternalIdQueryParams
  PagingParams)

(defroutes coa-routes
  (context "/coa" []
           :tags ["COA"]
           (POST "/" []
                 :return COA
                 :body [coa NewCOA {:description "a new COA"}]
                 :summary "Adds a new COA"
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :capabilities :create-coa
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow :realize-fn ent/realize-coa
                                        :store-fn #(write-store :coa
                                                                create-coas
                                                                %
                                                                identity-map)
                                        :long-id-fn with-long-id
                                        :entity-type :coa
                                        :identity identity
                                        :entities [coa]
                                        :spec :new-coa/map)
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return COA
                :body [coa NewCOA {:description "an updated COA"}]
                :summary "Updates a COA"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :create-coa
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow :get-fn #(read-store :coa
                                                            read-coa
                                                            %
                                                            identity-map)
                                       :realize-fn ent/realize-coa
                                       :update-fn #(write-store :coa
                                                                update-coa
                                                                (:id %)
                                                                %
                                                                identity-map)
                                       :long-id-fn with-long-id
                                       :entity-type :coa
                                       :entity-id id
                                       :identity identity
                                       :entity coa
                                       :spec :new-coa/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return (s/maybe [COA])
                :query [q COAByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List COAs by external id"
                :capabilities #{:read-coa :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :coa
                                list-coas
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return (s/maybe [COA])
                :summary "Search for a Course of Action using a Lucene/ES query string"
                :query [params COASearchParams]
                :capabilities #{:read-coa :search-coa}
                :identity identity
                :identity-map identity-map
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                (-> (query-string-search-store
                     :coa
                     query-string-search
                     (:query params)
                     (dissoc params :query :sort_by :sort_order :offset :limit)
                     identity-map
                     (select-keys params [:sort_by :sort_order :offset :limit]))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe COA)
                :summary "Gets a COA by ID"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-coa
                :identity identity
                :identity-map identity-map
                (if-let [coa (read-store :coa (fn [s] (read-coa s id identity-map)))]
                  (-> coa
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes a COA"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-coa
                   :identity identity
                   :identity-map identity-map
                   (if (flows/delete-flow :get-fn #(read-store :coa
                                                               read-coa
                                                               %
                                                               identity-map)
                                          :delete-fn #(write-store :coa
                                                                   delete-coa
                                                                   %
                                                                   identity-map)
                                          :entity-type :coa
                                          :entity-id id
                                          :identity identity)
                     (no-content)
                     (not-found)))))
