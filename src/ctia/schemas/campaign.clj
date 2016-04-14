(ns ctia.schemas.campaign
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st]))

(s/defschema Campaign
  "See http://stixproject.github.io/data-model/1.2/campaign/CampaignType/"
  (merge
   c/GenericStixIdentifiers
   {:valid_time
    (describe c/ValidTime "timestamp for the definition of a specific version of a Campaign")
    (s/optional-key :version)
    (describe s/Str "schema version for this content")
    (s/optional-key :names)
    (describe [s/Str] "Names used to identify this Campaign")
    (s/optional-key :intended_effect)
    (describe [v/IntendedEffect] "characterizes the intended effect of this cyber threat Campaign")
    (s/optional-key :status)
    (describe v/CampaignStatus "status of this Campaign")
    (s/optional-key :related_TTPs)
    (describe rel/RelatedTTPs "specifies TTPs asserted to be related to this cyber threat Campaign")
    (s/optional-key :related_incidents)
    (describe rel/RelatedIncidents "identifies or characterizes one or more Incidents related to this cyber threat Campaign")
    (s/optional-key :attribution)
    (describe rel/RelatedActors "assertions of attibuted Threat Actors for this cyber threat Campaign")
    (s/optional-key :associated_campaigns)
    (describe rel/RelatedCampaigns "other cyber threat Campaigns asserted to be associated with this cyber threat Campaign")
    (s/optional-key :confidence)
    (describe v/HighMedLow "level of confidence held in the characterization of this Campaign")
    (s/optional-key :activity)
    (describe c/Activity "actions taken in regards to this Campaign")
    (s/optional-key :source)
    (describe s/Str "source of this Campaign")
    ;; Extension fields:
    :campaign_type  s/Str
    :indicators rel/RelatedIndicators

    ;; Not provided: Handling
    ;; Not provided: related_packages (deprecated)
    }))

(s/defschema Type
  (s/enum "campaign"))

(s/defschema NewCampaign
  "Schema for submitting new Campaigns"
  (st/merge
   (st/dissoc Campaign
              :id
              :valid_time)
   {(s/optional-key :valid_time) c/ValidTime
    (s/optional-key :type) Type}))


(s/defschema StoredCampaign
  "An campaign as stored in the data store"
  (c/stored-schema "campaign" Campaign))

(def realize-campaign
  (c/default-realize-fn "campaign" NewCampaign StoredCampaign))
