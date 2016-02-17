(ns cia.schemas.campaign
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema Campaign
  "See http://stixproject.github.io/data-model/1.2/campaign/CampaignType/"
  (merge
   c/GenericStixIdentifiers
   {:timestamp c/Time
    (s/optional-key :version) s/Str
    (s/optional-key :names) [s/Str]
    (s/optional-key :intended_effect) [v/IntendedEffect]
    (s/optional-key :status) v/CampaignStatus
    (s/optional-key :related_TTPs) rel/RelatedTTPs
    (s/optional-key :related_incidents) rel/RelatedIncidents
    (s/optional-key :attribution) rel/RelatedActors
    (s/optional-key :associated_campaigns) rel/RelatedCampaigns
    (s/optional-key :confidence) v/HighMedLow
    (s/optional-key :activity) c/Activity
    (s/optional-key :source) s/Str

    ;; Extension fields:
    :type  s/Str
    :expires c/Time
    :indicators rel/RelatedIndicators

    ;; Not provided: Handling
    ;; Not provided: related_packages (deprecated)
    }))

(s/defschema NewCampaign
  "Schema for submitting new Campaigns"
  (st/merge (st/dissoc Campaign
                       :id
                       :expires)
            {(s/optional-key :expires) c/Time}))

(s/defn realize-campaign :- Campaign
  [new-campaign :- NewCampaign
   id :- s/Str]
  (assoc new-campaign
         :id id
         :expires (or (:expires new-campaign) (c/expire-after))))
