(ns ctia.http.routes.campaign
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.campaign :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created
            paginated-ok
            search-options
            filter-map-search-options
            PagingParams
            CampaignByExternalIdQueryParams
            CampaignGetParams
            CampaignSearchParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core
    :refer [NewCampaign
            Campaign
            PartialCampaign
            PartialCampaignList]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(defroutes campaign-routes
  (context "/campaign" []
           :tags ["Campaign"]
           (POST "/" []
                 :return Campaign
                 :body [campaign NewCampaign {:description "a new campaign"}]
                 :summary "Adds a new Campaign"
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :capabilities :create-campaign
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :realize-fn ent/realize-campaign
                      :store-fn #(write-store :campaign
                                              create-campaigns
                                              %
                                              identity-map
                                              {})
                      :long-id-fn with-long-id
                      :entity-type :campaign
                      :identity identity
                      :entities [campaign]
                      :spec :new-campaign/map)
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return Campaign
                :body [campaign NewCampaign {:description "an updated campaign"}]
                :summary "Updates a Campaign"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :create-campaign
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow
                     :get-fn #(read-store :campaign
                                          read-campaign
                                          %
                                          identity-map
                                          {})
                     :realize-fn ent/realize-campaign
                     :update-fn #(write-store :campaign
                                              update-campaign
                                              (:id %)
                                              %
                                              identity-map)
                     :long-id-fn with-long-id
                     :entity-type :campaign
                     :entity-id id
                     :identity identity
                     :entity campaign
                     :spec :new-campaign/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return PartialCampaignList
                :query [q CampaignByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List campaigns by external id"
                :capabilities #{:read-campaign :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :campaign
                                list-campaigns
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return PartialCampaignList
                :summary "Search for a Campaign using a Lucene/ES query string"
                :query [params CampaignSearchParams]
                :capabilities #{:read-campaign :search-campaign}
                :identity identity
                :identity-map identity-map
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                (-> (query-string-search-store
                     :campaign
                     query-string-search
                     (:query params)
                     (apply dissoc params filter-map-search-options)
                     identity-map
                     (select-keys params search-options))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe PartialCampaign)
                :summary "Gets a Campaign by ID"
                :path-params [id :- s/Str]
                :query [params CampaignGetParams]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-campaign
                :identity identity
                :identity-map identity-map
                (if-let [campaign (read-store :campaign
                                              read-campaign
                                              id
                                              identity-map
                                              params)]
                  (-> campaign
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes a Campaign"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-campaign
                   :identity identity
                   :identity-map identity-map
                   (if (flows/delete-flow
                        :get-fn #(read-store :campaign
                                             read-campaign
                                             %
                                             identity-map
                                             {})
                        :delete-fn #(write-store :campaign
                                                 delete-campaign
                                                 %
                                                 identity-map)
                        :entity-type :campaign
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
