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
    :type  s/Str
    :indicators rel/RelatedIndicators

    ;; Not provided: Handling
    ;; Not provided: related_packages (deprecated)
    }))

(s/defschema NewCampaign
  "Schema for submitting new Campaigns"
  (st/merge
   (st/dissoc Campaign
              :id
              :valid_time)
   {(s/optional-key :valid_time) c/ValidTime}))

(s/defschema StoredCampaign
  "A schema as stored in the data store"
  (st/merge Campaign
            {:owner s/Str
             :created c/Time}))

(s/defn realize-campaign :- StoredCampaign
  [new-campaign :- NewCampaign
   id :- s/Str]
  (let [now (c/timestamp)]
    (assoc new-campaign
           :id id
           :owner "not implemented"
           :created now
           :valid_time {:end_time (or (get-in new-campaign [:valid_time :end_time])
                                      c/default-expire-date)
                        :start_time (or (get-in new-campaign [:valid_time :start_time])
                                        now)})))
