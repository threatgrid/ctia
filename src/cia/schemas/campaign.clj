(ns cia.schemas.campaign
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]))

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
    (s/optional-key :attribution) rel/AttributedActors
    (s/optional-key :associated_campaigns) rel/AssociatedCampaigns
    (s/optional-key :confidence) v/HighMedLow
    (s/optional-key :activity) c/Activity
    (s/optional-key :source) c/Source

    ;; Extension fields:
    :type  s/Str
    :expires c/Time
    :indicators rel/RelatedIndicators

    ;; Not provided: Handling
    ;; Not provided: related_packages (deprecated)
    }))
