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
   {:valid_time c/ValidTime
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
    :indicators rel/RelatedIndicators

    ;; Not provided: Handling
    ;; Not provided: related_packages (deprecated)
    }))

(s/defschema NewCampaign
  "Schema for submitting new Campaigns"
  (st/dissoc Campaign
             :id))

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
