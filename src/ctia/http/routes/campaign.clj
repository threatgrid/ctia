(ns ctia.http.routes.campaign
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.campaign :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created paginated-ok PagingParams CampaignSearchParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewCampaign Campaign]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema CampaignByExternalIdQueryParams
  PagingParams)

(defroutes campaign-routes
  (context "/campaign" []
           :tags ["Campaign"]
           (POST "/" []
                 :return Campaign
                 :body [campaign NewCampaign {:description "a new campaign"}]
                 :summary "Adds a new Campaign"
                 :header-params [api_key :- (s/maybe s/Str)]
                 :capabilities :create-campaign
                 :identity identity
                 (-> (flows/create-flow
                      :realize-fn ent/realize-campaign
                      :store-fn #(write-store :campaign create-campaigns %)
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
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :create-campaign
                :identity identity
                (-> (flows/update-flow
                     :get-fn #(read-store :campaign read-campaign %)
                     :realize-fn ent/realize-campaign
                     :update-fn #(write-store :campaign update-campaign (:id %) %)
                     :long-id-fn with-long-id
                     :entity-type :campaign
                     :entity-id id
                     :identity identity
                     :entity campaign
                     :spec :new-campaign/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return (s/maybe [Campaign])
                :query [q CampaignByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "List campaigns by external id"
                :capabilities #{:read-campaign :external-id}
                (-> (read-store :campaign list-campaigns
                                {:external_ids external_id} q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return (s/maybe [Campaign])
                :summary "Search for a Campaign using a Lucene/ES query string"
                :query [params CampaignSearchParams]
                :capabilities #{:read-campaign :search-campaign}
                :header-params [api_key :- (s/maybe s/Str)]
                (-> (query-string-search-store
                     :campaign
                     query-string-search
                     (:query params)
                     (dissoc params :query :sort_by :sort_order :offset :limit)
                     (select-keys params [:sort_by :sort_order :offset :limit]))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe Campaign)
                :summary "Gets a Campaign by ID"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :read-campaign
                (if-let [campaign (read-store :campaign read-campaign id)]
                  (-> campaign
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes a Campaign"
                   :header-params [api_key :- (s/maybe s/Str)]
                   :capabilities :delete-campaign
                   :identity identity
                   (if (flows/delete-flow
                        :get-fn #(read-store :campaign read-campaign %)
                        :delete-fn #(write-store :campaign delete-campaign %)
                        :entity-type :campaign
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
