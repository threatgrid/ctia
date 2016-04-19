(ns ctia.schemas.incident
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st]))

(s/defschema COARequested
  "See http://stixproject.github.io/data-model/1.2/incident/COARequestedType/
   and http://stixproject.github.io/data-model/1.2/incident/COATakenType/"
  (st/merge
   (st/optional-keys
    {:time (describe c/Time "relative time criteria for this taken CourseOfActio")
     :contributors (describe [c/Contributor]
                             "contributing actors for the CourseOfAction taken")}
    )
   {:COA (describe rel/COAReference "COA reference")}))

(s/defschema NonPublicDataCompromised
  "See http://stixproject.github.io/data-model/1.2/incident/NonPublicDataCompromisedType/"
  ;; Simplified schema
  {:security_compromise (describe v/SecurityCompromise
                                  "related security compromise")
   (s/optional-key
    :data_encrypted) (describe s/Bool
                               (str "whether the data that was compromised"
                                    " was encrypted or not"))})

(s/defschema PropertyAffected
  "See http://stixproject.github.io/data-model/1.2/incident/PropertyAffectedType/"
  (st/optional-keys
   {:property (describe v/LossProperty
                        "security property that was affected by the incident")
    :description_of_effect (describe
                            s/Str
                            (str "a brief prose description of how the security"
                                 " property was affected"))
    :type_of_availability_loss (describe
                                s/Str
                                (str "characterizes in what manner the"
                                     " availability of this asset was affected")) ;; Vocab is empty
    :duration_of_availability_loss (describe
                                    v/LossDuration
                                    (str "approximate length of time availability"
                                         " was affected"))
    :non_public_data_compromised (describe
                                  NonPublicDataCompromised
                                  (str "approximate length of time availability"
                                       " was affected"))}))

(s/defschema AffectedAsset
  "See http://stixproject.github.io/data-model/1.2/incident/AffectedAssetType/"
  (st/optional-keys
   {:type (describe s/Str
                    (str "type of the asset impacted by the incident"
                         " (a security attribute was negatively affected)."))
    :description (describe s/Str "text description of the asset")
    :ownership_class (describe v/OwnershipClass
                               (str "high-level characterization of who owns"
                                    " (or controls) this asset"))
    :management_class (describe
                       v/ManagementClass
                       (str "high-level characterization of who is responsible"
                            " for the day-to-day management and administration"
                            " of this asset"))
    :location_class (describe
                     v/LocationClass
                     (str "high-level characterization of where this asset is"
                          " physically located"))
    :property_affected (describe PropertyAffected "affected property") ;; Unnested NatureOfSecurityEffect
    :identifying_observables [c/Observable]
    ;; Not Provided: business_function_or_role
    ;; Not Provided: location (undefined/abstract type); Could be [s/Str]
    }))

(s/defschema DirectImpactSummary
  "See http://stixproject.github.io/data-model/1.2/incident/DirectImpactSummaryType/"
  (st/optional-keys
   {:asset_losses (describe
                   v/ImpactRating
                   "level of asset-related losses that occured in the Incident")
    :business_mission_distruption (describe
                                   v/ImpactRating
                                   (str "characterizes (at a high level)"
                                        " the level of business or mission"
                                        " disruption impact that occured in"
                                        " the Incident"))
    :response_and_recovery_costs (describe
                                  v/ImpactRating
                                  (str "characterizes (at a high level)"
                                       " the level of response and recovery"
                                       " related costs that occured in"
                                       " the Incident"))}))

(s/defschema IndirectImpactSummary
  "See http://stixproject.github.io/data-model/1.2/incident/IndirectImpactSummaryType/"
  (st/optional-keys
   {:loss_of_competitive_advantage (describe
                                    v/SecurityCompromise
                                    (str "characterizes (at a high level)"
                                         " the level of impact based on loss of"
                                         " competitive advantage that occured in"
                                         " the Incident"))
    :brand_and_market_damage (describe
                              v/SecurityCompromise
                              (str "characterizes (at a high level)"
                                   " the level of impact based on brand or"
                                   " market damage that occured in the Incident"))
    :increased_operating_costs (describe
                                v/SecurityCompromise
                                (str "characterizes (at a high level) the level"
                                     " of impact based on increased operating"
                                     " costs that occured in the Incident"))
    :local_and_regulatory_costs v/SecurityCompromise}))

(s/defschema LossEstimation
  "See http://stixproject.github.io/data-model/1.2/incident/LossEstimationType/"
  (st/optional-keys
   {:amount (describe s/Num "the estimated financial loss for the Incident")
    :iso_currency_code (describe s/Str "ISO 4217 currency code if other than USD")}))

(s/defschema TotalLossEstimation
  "See http://stixproject.github.io/data-model/1.2/incident/TotalLossEstimationType/"
  (st/optional-keys
   {:initial_reported_total_loss_estimation (describe
                                             LossEstimation
                                             (str "specifies the initially reported"
                                                  " level of total estimated"
                                                  " financial loss for the Incident"))
    :actual_total_loss_estimation (describe
                                   LossEstimation
                                   (str "specifies the actual level of total"
                                        " estimated financial loss for the Incident"))}))

(s/defschema ImpactAssessment
  "See http://stixproject.github.io/data-model/1.2/incident/ImpactAssessmentType/"
  (st/optional-keys
   {:direct_impact_summary (describe
                            DirectImpactSummary
                            (str "characterizes (at a high level) losses directly"
                                 " resulting from the ThreatActor's actions"
                                 " against organizational assets within the Incident"))
    :indirect_impact_summary (describe
                              IndirectImpactSummary
                              (str "characterizes (at a high level) losses from"
                                   " other stakeholder reactions to the Incident"))
    :total_loss_estimation (describe
                            TotalLossEstimation
                            "specifies the total estimated financial loss for the Incident")
    :impact_qualification (describe
                           v/ImpactQualification
                           "summarizes the subjective level of impact of the Incident")
    :effects (describe
              [v/Effect]
              "list of effects of this incident from a controlled vocabulary")
    ;; Not provided: external_impact_assessment_model
    }))

(s/defschema IncidentTime
  "See http://stixproject.github.io/data-model/1.2/incident/TimeType/"
  (st/optional-keys
   {:first_malicious_action c/Time ;; Simplified structure
    :initial_compromise c/Time
    :first_data_exfiltration c/Time
    :incident_discovery c/Time
    :incident_opened c/Time
    :containment_achieved c/Time
    :restoration_achieved c/Time
    :incident_reported c/Time
    :incident_closed c/Time}))

(s/defschema History
  "See http://stixproject.github.io/data-model/1.2/incident/HistoryItemType/"
  (st/optional-keys
   {:action_entry (describe
                   [COARequested]
                   "a record of actions taken during the handling of the Incident")
    :journal_entry (describe
                    s/Str
                    (str "journal notes for information discovered"
                         " during the handling of the Incident")) ;; simplified
    }))

(s/defschema Incident
  "See http://stixproject.github.io/data-model/1.2/incident/IncidentType/"
  (st/merge
   c/GenericStixIdentifiers
   {:valid_time (describe
                 c/ValidTime
                 "timestamp for the definition of a specific version of an Incident")
    :confidence (describe
                 v/HighMedLow
                 "level of confidence held in the characterization of this Incident")}
   (st/optional-keys
    {:status (describe v/Status "current status of the incident")
     :version (describe s/Str "schema version for this content")
     :incident_time (describe
                     IncidentTime
                     "relevant time values associated with this Incident") ;; Was "time"; renamed for clarity
     :categories (describe [v/IncidentCategory]
                           "a set of categories for this incident")
     :reporter (describe s/Str
                         "information about the reporting source of this Incident")
     :responder (describe
                 s/Str
                 "information about the assigned responder for this Incident")
     :coordinator (describe
                   s/Str
                   "information about the assigned coordinator for this Incident")
     :victim (describe s/Str "information about a victim of this Incident")
     :affected_assets (describe [AffectedAsset]
                                "particular assets affected during the Incident")
     :impact_assessment (describe
                         ImpactAssessment
                         "a summary assessment of impact for this cyber threat Incident")
     :source s/Str
     :security_compromise (describe
                           v/SecurityCompromise
                           (str "knowledge of whether the Incident involved a"
                                " compromise of security properties"))
     :discovery_method (describe v/DiscoveryMethod
                                 "identifies how the incident was discovered")
     :COA_requested (describe
                     [COARequested]
                     (str "specifies and characterizes requested Course Of Action"
                          " for this Incident as specified by the Producer for"
                          " the Consumer of the Incident Report"))
     :COA_taken (describe [COARequested]
                          (str "specifies and characterizes a Course Of Action"
                               " taken for this Incident"))
     :contact (describe s/Str
                        (str "identifies and characterizes organizations or"
                             " personnel involved in this Incident"))
     :history (describe [History]
                        (str "a log of events or actions taken during"
                             " the handling of the Incident"))

     ;; The seqs of elements below are squashed (they leave out
     ;; structured data such as confidence and source for each element).
     :related_indicators (describe
                          rel/RelatedIndicators
                          (str "identifies or characterizes one or more cyber"
                               " threat Indicators related to this cyber threat Incident"))
     :related_observables (describe
                           [c/Observable]
                           (str
                            "identifies or characterizes one or more cyber"
                            " observables related to this cyber threat incident")) ;; Was related_observables
     :leveraged_TTPs (describe
                      rel/RelatedTTPs
                      (str
                       "specifies TTPs asserted to be related to this cyber"
                       " threat Incident"))
     :attributed_actors (describe
                         rel/RelatedActors
                         (str
                          "identifies ThreatActors asserted to be attributed for"
                          " this Incident")) ;; was attributed_threat_actors
     :related_incidents (describe
                         rel/RelatedIncidents
                         (str "identifies or characterizes one or more other"
                              " Incidents related to this cyber threat Incident"))
     :intended_effect (describe
                       v/IntendedEffect
                       "specifies the suspected intended effect of this incident")
     ;; Not provided: URL
     ;; Not provided: external_id
     ;; Not provided: handling
     ;; Not provided: related_packages (deprecated)
     })))

(s/defschema Type
  (s/enum "incident"))

(s/defschema NewIncident
  (st/merge
   (st/dissoc Incident
              :id
              :valid_time)
   {(s/optional-key :valid_time) c/ValidTime
    (s/optional-key :type) Type}))


(s/defschema StoredIncident
  "An incident as stored in the data store"
  (c/stored-schema "incident" Incident))

(def realize-incident
  (c/default-realize-fn "incident" NewIncident StoredIncident))
