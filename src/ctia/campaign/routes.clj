(ns ctia.campaign.routes
  (:require [ctia.domain.entities
             :refer [realize-campaign]]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [Campaign NewCampaign PartialCampaign PartialCampaignList]]
             [sorting :as sorting]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def campaign-sort-fields
  (apply s/enum sorting/campaign-sort-fields))

(s/defschema CampaignFieldsParam
  {(s/optional-key :fields) [campaign-sort-fields]})

(s/defschema CampaignSearchParams
  (st/merge
   PagingParams
   BaseEntityFilterParams
   SourcableEntityFilterParams
   CampaignFieldsParam
   {:query s/Str
    (s/optional-key :campaign_type) s/Str
    (s/optional-key :confidence) s/Str
    (s/optional-key :activity) s/Str
    (s/optional-key :sort_by)  campaign-sort-fields}))

(def CampaignGetParams CampaignFieldsParam)

(s/defschema CampaignByExternalIdQueryParams
  (st/merge
   PagingParams
   CampaignFieldsParam))

(def campaign-routes
  (entity-crud-routes
   {:api-tags ["Campaign"]
    :entity :campaign
    :new-schema NewCampaign
    :entity-schema Campaign
    :get-schema PartialCampaign
    :get-params CampaignGetParams
    :list-schema PartialCampaignList
    :search-schema PartialCampaignList
    :external-id-q-params CampaignByExternalIdQueryParams
    :search-q-params CampaignSearchParams
    :new-spec :new-campaign/map
    :realize-fn realize-campaign
    :get-capabilities :read-campaign
    :post-capabilities :create-campaign
    :put-capabilities :create-campaign
    :delete-capabilities :delete-campaign
    :search-capabilities :search-campaign
    :external-id-capabilities #{:read-campaign :external-id}}))
