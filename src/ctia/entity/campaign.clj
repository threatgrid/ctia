(ns ctia.entity.campaign
  (:require
   [ctia.domain.entities :refer [default-realize-fn]]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.schemas.core :refer [APIHandlerServices def-acl-schema def-stored-schema]]
   [ctia.schemas.sorting :as sorting :refer [default-entity-sort-fields]]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
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

(def-stored-schema StoredCampaign Campaign)

(s/defschema PartialStoredCampaign
  (st/optional-keys-schema StoredCampaign))

(def campaign-fields
  (concat default-entity-sort-fields
          [:valid_time.start_time
           :valid_time.end_time
           :campaign_type
           :status
           :activity.date_time
           :confidence]))

(def campaign-sort-fields
  (apply s/enum campaign-fields))

(def realize-campaign
  (default-realize-fn "campaign" NewCampaign StoredCampaign))

(def campaign-mapping
  {"campaign"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:valid_time      em/valid-time
      :campaign_type   em/token
      :names           em/token
      :intended_effect em/token
      :status          em/token
      :confidence      em/token
      :activity        em/activity})}})

(def-es-store CampaignStore :campaign StoredCampaign PartialStoredCampaign)

(s/defschema CampaignFieldsParam
  {(s/optional-key :fields) [campaign-sort-fields]})

(s/defschema CampaignSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   CampaignFieldsParam
   (st/optional-keys
    {:campaign_type s/Str
     :confidence    s/Str
     :activity      s/Str
     :sort_by       campaign-sort-fields})))

(def campaign-histogram-fields
  [:timestamp
   :valid_time.start_time
   :valid_time.end_time])

(def campaign-enumerable-fields
  [:source
   :campaign_type
   :confidence
   :status])

(def CampaignGetParams CampaignFieldsParam)

(s/defschema CampaignByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   CampaignFieldsParam))

(s/defn campaign-routes [services :- APIHandlerServices]
  (services->entity-crud-routes
   services
   {:api-tags                 ["Campaign"]
    :entity                   :campaign
    :new-schema               NewCampaign
    :entity-schema            Campaign
    :get-schema               PartialCampaign
    :get-params               CampaignGetParams
    :list-schema              PartialCampaignList
    :search-schema            PartialCampaignList
    :external-id-q-params     CampaignByExternalIdQueryParams
    :search-q-params          CampaignSearchParams
    :new-spec                 :new-campaign/map
    :realize-fn               realize-campaign
    :get-capabilities         :read-campaign
    :post-capabilities        :create-campaign
    :put-capabilities         :create-campaign
    :delete-capabilities      :delete-campaign
    :search-capabilities      :search-campaign
    :external-id-capabilities :read-campaign
    :can-aggregate?           true
    :histogram-fields         campaign-histogram-fields
    :enumerable-fields        campaign-enumerable-fields
    :searchable-fields        (routes.common/searchable-fields
                               campaign-fields)}))

(def capabilities
  #{:create-campaign
    :read-campaign
    :delete-campaign
    :search-campaign})

(def campaign-entity
  {:route-context         "/campaign"
   :tags                  ["Campaign"]
   :entity                :campaign
   :plural                :campaigns
   :new-spec              :new-campaign/map
   :schema                Campaign
   :partial-schema        PartialCampaign
   :partial-list-schema   PartialCampaignList
   :new-schema            NewCampaign
   :stored-schema         StoredCampaign
   :partial-stored-schema PartialStoredCampaign
   :realize-fn            realize-campaign
   :es-store              ->CampaignStore
   :es-mapping            campaign-mapping
   :services->routes      (routes.common/reloadable-function campaign-routes)
   :capabilities          capabilities
   :fields                campaign-fields
   :sort-fields           campaign-fields})
