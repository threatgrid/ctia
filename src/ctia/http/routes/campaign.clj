(ns ctia.http.routes.campaign
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :refer [realize-campaign]]
   [ctia.domain.entities.campaign :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :refer [created paginated-ok PagingParams CampaignSearchParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewCampaign StoredCampaign]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema CampaignByExternalIdQueryParams
  (st/merge
   PagingParams
   {:external_id s/Str}))

(defroutes campaign-routes
  (context "/campaign" []
    :tags ["Campaign"]
    (POST "/" []
      :return StoredCampaign
      :body [campaign NewCampaign {:description "a new campaign"}]
      :summary "Adds a new Campaign"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-campaign
      :identity identity
      (created
       (with-long-id
         (first
          (flows/create-flow :realize-fn realize-campaign
                             :store-fn #(write-store :campaign create-campaigns %)
                             :entity-type :campaign
                             :identity identity
                             :entities [campaign])))))
    (PUT "/:id" []
      :return StoredCampaign
      :body [campaign NewCampaign {:description "an updated campaign"}]
      :summary "Updates a Campaign"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-campaign
      :identity identity
      (ok
       (with-long-id
         (flows/update-flow :get-fn #(read-store :campaign read-campaign %)
                            :realize-fn realize-campaign
                            :update-fn #(write-store :campaign update-campaign (:id %) %)
                            :entity-type :campaign
                            :entity-id id
                            :identity identity
                            :entity campaign))))
    (GET "/external_id" []
      :return [(s/maybe StoredCampaign)]
      :query [q CampaignByExternalIdQueryParams]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "List campaigns by external id"
      :capabilities #{:read-campaign :external-id}
      (paginated-ok
       (page-with-long-id
        (read-store :campaign list-campaigns
                    {:external_ids (:external_id q)} q))))

    (GET "/search" []
         :return (s/maybe [StoredCampaign])
         :summary "Search for a Campaign using a Lucene/ES query string"
         :query [params CampaignSearchParams]
         :capabilities #{:read-campaign :search-campaign}
         :header-params [api_key :- (s/maybe s/Str)]
         (paginated-ok
          (page-with-long-id
           (query-string-search-store :campaign
                                      query-string-search
                                      (:query params)
                                      (dissoc params :query :sort_by :sort_order :offset :limit)
                                      (select-keys params [:sort_by :sort_order :offset :limit])))))

    (GET "/:id" []
      :return (s/maybe StoredCampaign)
      :summary "Gets a Campaign by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-campaign
      (if-let [d (read-store :campaign read-campaign id)]
        (ok (with-long-id d))
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a Campaign"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-campaign
      :identity identity
      (if (flows/delete-flow :get-fn #(read-store :campaign read-campaign %)
                             :delete-fn #(write-store :campaign delete-campaign %)
                             :entity-type :campaign
                             :entity-id id
                             :identity identity)
        (no-content)
        (not-found)))))
