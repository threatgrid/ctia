(ns ctia.entity.campaign
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.store :refer :all]
            [ctia.http.routes
             [common :refer [BaseEntityFilterParams PagingParams SourcableEntityFilterParams]]
             [crud :refer [entity-crud-routes]]]
            [ctia.schemas
             [core :refer [def-acl-schema def-stored-schema]]
             [sorting :as sorting :refer [default-entity-sort-fields]]]
            [ctia.stores.es
             [mapping :as em]
             [store :refer [def-es-store]]]
            [ctim.schemas.campaign :as cs]
            [flanders.utils :as fu]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def-acl-schema Campaign
  cs/Campaign
  "campaign")

(def-acl-schema PartialCampaign
  (fu/optionalize-all cs/Campaign)
  "partial-campaign")

(s/defschema PartialCampaignList
  [PartialCampaign])

(def-acl-schema NewCampaign
  cs/NewCampaign
  "new-campaign")

(def-stored-schema StoredCampaign
  cs/StoredCampaign
  "stored-campaign")

(def-stored-schema PartialStoredCampaign
  (fu/optionalize-all cs/StoredCampaign)
  "partial-stored-campaign")

(def campaign-fields
  (concat default-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time
           :campaign_type
           :status
           :activity.date_time
           :activity.description
           :confidence]))

(def campaign-sort-fields
  (apply s/enum campaign-fields))

(def realize-campaign
  (default-realize-fn "campaign" NewCampaign StoredCampaign))

(def campaign-mapping
  {"campaign"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:valid_time em/valid-time
      :campaign_type em/token
      :names em/all_token
      :intended_effect em/token
      :status em/token
      :confidence em/token
      :activity em/activity})}})

(def-es-store CampaignStore :campaign StoredCampaign PartialStoredCampaign)

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

(def capabilities
  #{:create-campaign
    :read-campaign
    :delete-campaign
    :search-campaign})

(def campaign-entity
  {:route-context "/campaign"
   :tags ["Campaign"]
   :entity :campaign
   :plural :campaigns
   :schema Campaign
   :partial-schema PartialCampaign
   :partial-list-schema PartialCampaignList
   :new-schema NewCampaign
   :stored-schema StoredCampaign
   :partial-stored-schema PartialStoredCampaign
   :realize-fn realize-campaign
   :es-store ->CampaignStore
   :es-mapping campaign-mapping
   :routes campaign-routes
   :capabilities capabilities})
