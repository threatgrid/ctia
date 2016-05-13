(ns ctia.schemas.campaign
  (:require [ctia.schemas
             [common :as c]
             [relationships :as rel]
             [vocabularies :as v]]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defschema Campaign
  "See http://stixproject.github.io/data-model/1.2/campaign/CampaignType/"
  (st/merge
   c/GenericStixIdentifiers
   {:valid_time (describe
                 c/ValidTime
                 "timestamp for the definition of a specific version of a Campaign")
    ;; Extension fields:
    :campaign_type  s/Str
    :indicators rel/RelatedIndicators
    :tlp c/TLP}
   (st/optional-keys
    {:version (describe s/Str "schema version for this content")
     :names (describe [s/Str] "Names used to identify this Campaign")
     :intended_effect (describe
                       [v/IntendedEffect]
                       (str "characterizes the intended effect of"
                            " this cyber threat Campaign"))
     :status (describe v/CampaignStatus "status of this Campaign")
     :related_TTPs (describe
                    rel/RelatedTTPs
                    (str "specifies TTPs asserted to be related to"
                         " this cyber threat Campaign"))
     :related_incidents (describe
                         rel/RelatedIncidents
                         (str "identifies or characterizes one or more Incidents"
                              " related to this cyber threat Campaign"))
     :attribution (describe rel/RelatedActors
                            (str
                             "assertions of attibuted Threat Actors"
                             " for this cyber threat Campaign"))
     :associated_campaigns (describe
                            rel/RelatedCampaigns
                            (str "other cyber threat Campaigns asserted to"
                                 " be associated with this cyber threat Campaign"))
     :confidence (describe v/HighMedLow
                           (str "level of confidence held in"
                                " the characterization of this Campaign"))
     :activity (describe c/Activity
                         "actions taken in regards to this Campaign")
     :source (describe s/Str "source of this Campaign")
     ;; Not provided: Handling
     ;; Not provided: related_packages (deprecated)
     })))

(s/defschema Type
  (s/enum "campaign"))

(s/defschema NewCampaign
  "Schema for submitting new Campaigns"
  (st/merge
   Campaign
   (st/optional-keys
    {:id c/ID
     :valid_time c/ValidTime
     :type Type
     :tlp c/TLP})))


(s/defschema StoredCampaign
  "An campaign as stored in the data store"
  (c/stored-schema "campaign" Campaign))

(def realize-campaign
  (c/default-realize-fn "campaign" NewCampaign StoredCampaign))
