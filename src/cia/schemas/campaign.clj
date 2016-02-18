(ns cia.schemas.campaign
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st]))

(s/defschema Campaign
  "See http://stixproject.github.io/data-model/1.2/campaign/CampaignType/"
  (merge
   c/GenericStixIdentifiers
   {:timestamp c/Time
    (s/optional-key :version)
    (describe s/Str "schema version for this content")
    (s/optional-key :names)
    (describe [s/Str] "Names used to identify this Campaign")
    (s/optional-key :intended_effect)
    (describe [v/IntendedEffect] "intended effect of this cyber threat Campaign")
    (s/optional-key :status)
    (describe v/CampaignStatus "The status of this Campaign")
    (s/optional-key :related_TTPs)
    (describe rel/RelatedTTPs "TTPs asserted to be related to this cyber threat Campaign")
    (s/optional-key :related_incidents)
    (describe rel/RelatedIncidents "one or more Incidents related to this cyber threat Campaign")
    (s/optional-key :attribution)
    (describe rel/RelatedActors "assertions of attibuted Threat Actors for this cyber threat Campaign")
    (s/optional-key :associated_campaigns)
    (describe rel/RelatedCampaigns "assertions of attibuted Threat Actors for this cyber threat Campaign")
    (s/optional-key :confidence)
    (describe v/HighMedLow "level of confidence held in the characterization of this Campaign.")
    (s/optional-key :activity)
    (describe c/Activity "actions taken in regards to this Campaign")
    (s/optional-key :source) c/Source

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
