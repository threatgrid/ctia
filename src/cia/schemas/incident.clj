(ns cia.schemas.incident
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st]))

(s/defschema COARequested
  "See http://stixproject.github.io/data-model/1.2/incident/COARequestedType/
   and http://stixproject.github.io/data-model/1.2/incident/COATakenType/"
  {(s/optional-key :time)
   (describe c/Time "relative time criteria for this taken CourseOfActio")
   (s/optional-key :contributors)
   (describe [c/Contributor] "contributing actors for the CourseOfAction taken")
   :COA (describe rel/COAReference "COA reference")})

(s/defschema NonPublicDataCompromised
  "See http://stixproject.github.io/data-model/1.2/incident/NonPublicDataCompromisedType/"
  ;; Simplified schema
  {:security_compromise
   (describe v/SecurityCompromise "related security compromise")
   (s/optional-key :data_encrypted)
   (describe s/Bool "whether the data that was compromised was encrypted or not")})

(s/defschema PropertyAffected
  "See http://stixproject.github.io/data-model/1.2/incident/PropertyAffectedType/"
  {(s/optional-key :property)
   (describe v/LossProperty "security property that was affected by the incident")
   (s/optional-key :description_of_effect)
   (describe s/Str "a brief prose description of how the security property was affected")
   (s/optional-key :type_of_availability_loss)
   (describe s/Str "characterizes in what manner the availability of this asset was affected") ;; Vocab is empty
   (s/optional-key :duration_of_availability_loss)
   (describe v/LossDuration "approximate length of time availability was affected")
   (s/optional-key :non_public_data_compromised)
   (describe NonPublicDataCompromised "approximate length of time availability was affected")})

(s/defschema AffectedAsset
  "See http://stixproject.github.io/data-model/1.2/incident/AffectedAssetType/"
  {(s/optional-key :type)
   (describe s/Str "type of the asset impacted by the incident (a security attribute was negatively affected).")
   (s/optional-key :description)
   (describe s/Str "text description of the asset")
   (s/optional-key :ownership_class)
   (describe v/OwnershipClass "high-level characterization of who owns (or controls) this asset")
   (s/optional-key :management_class)
   (describe v/ManagementClass "high-level characterization of who is responsible for the day-to-day management and administration of this asset")
   (s/optional-key :location_class)
   (describe v/LocationClass "high-level characterization of where this asset is physically located")
   (s/optional-key :property_affected)
   (describe PropertyAffected "affected property") ;; Unnested NatureOfSecurityEffect
   (s/optional-key :identifying_observables) [c/Observable]
   ;; Not Provided: business_function_or_role
   ;; Not Provided: location (undefined/abstract type); Could be [s/Str]
   })

(s/defschema DirectImpactSummary
  "See http://stixproject.github.io/data-model/1.2/incident/DirectImpactSummaryType/"
  {(s/optional-key :asset_losses)
   (describe v/ImpactRating "level of asset-related losses that occured in the Incident")
   (s/optional-key :business_mission_distruption)
   (describe v/ImpactRating "characterizes (at a high level) the level of business or mission disruption impact that occured in the Incident")
   (s/optional-key :response_and_recovery_costs)
   (describe v/ImpactRating "characterizes (at a high level) the level of response and recovery related costs that occured in the Incident")})

(s/defschema IndirectImpactSummary
  "See http://stixproject.github.io/data-model/1.2/incident/IndirectImpactSummaryType/"
  {(s/optional-key :loss_of_competitive_advantage)
   (describe v/SecurityCompromise "characterizes (at a high level) the level of impact based on loss of competitive advantage that occured in the Incident")
   (s/optional-key :brand_and_market_damage)
   (describe v/SecurityCompromise "characterizes (at a high level) the level of impact based on brand or market damage that occured in the Incident")
   (s/optional-key :increased_operating_costs)
   (describe v/SecurityCompromise "characterizes (at a high level) the level of impact based on increased operating costs that occured in the Incident")
   (s/optional-key :local_and_regulatory_costs) v/SecurityCompromise})

(s/defschema LossEstimation
  "See http://stixproject.github.io/data-model/1.2/incident/LossEstimationType/"
  {(s/optional-key :amount)
   (describe s/Num "the estimated financial loss for the Incident")
   (s/optional-key :iso_currency_code)
   (describe s/Str "ISO 4217 currency code if other than USD")})

(s/defschema TotalLossEstimation
  "See http://stixproject.github.io/data-model/1.2/incident/TotalLossEstimationType/"
  {(s/optional-key :initial_reported_total_loss_estimation)
   (describe LossEstimation "specifies the initially reported level of total estimated financial loss for the Incident")
   (s/optional-key :actual_total_loss_estimation)
   (describe LossEstimation "specifies the actual level of total estimated financial loss for the Incident")})

(s/defschema ImpactAssessment
  "See http://stixproject.github.io/data-model/1.2/incident/ImpactAssessmentType/"
  {(s/optional-key :direct_impact_summary)
   (describe DirectImpactSummary "characterizes (at a high level) losses directly resulting from the ThreatActor's actions against organizational assets within the Incident")
   (s/optional-key :indirect_impact_summary)
   (describe IndirectImpactSummary "characterizes (at a high level) losses from other stakeholder reactions to the Incident")
   (s/optional-key :total_loss_estimation)
   (describe TotalLossEstimation "specifies the total estimated financial loss for the Incident")
   (s/optional-key :impact_qualification)
   (describe v/ImpactQualification "summarizes the subjective level of impact of the Incident")
   (s/optional-key :effects)
   (describe [v/Effect] "list of effects of this incident from a controlled vocabulary")
   ;; Not provided: external_impact_assessment_model
   })

(s/defschema IncidentTime
  "See http://stixproject.github.io/data-model/1.2/incident/TimeType/"
  {(s/optional-key :first_malicious_action) c/Time ;; Simplified structure
   (s/optional-key :initial_compromise) c/Time
   (s/optional-key :first_data_exfiltration) c/Time
   (s/optional-key :incident_discovery) c/Time
   (s/optional-key :incident_opened) c/Time
   (s/optional-key :containment_achieved) c/Time
   (s/optional-key :restoration_achieved) c/Time
   (s/optional-key :incident_reported) c/Time
   (s/optional-key :incident_closed) c/Time})

(s/defschema History
  "See http://stixproject.github.io/data-model/1.2/incident/HistoryItemType/"
  {(s/optional-key :action_entry)
   (describe [COARequested] "a record of actions taken during the handling of the Incident")
   (s/optional-key :journal_entry)
   (describe s/Str "journal notes for information discovered during the handling of the Incident") ;; simplified
   })

(s/defschema Incident
  "See http://stixproject.github.io/data-model/1.2/incident/IncidentType/"
  (st/merge
   c/GenericStixIdentifiers
   {:valid_time
    (describe c/ValidTime "timestamp for the definition of a specific version of an Incident")
    :confidence (describe v/HighMedLow "level of confidence held in the characterization of this Incident")
    (s/optional-key :status) (describe v/Status "current status of the incident")
    (s/optional-key :version) (describe s/Str "schema version for this content")
    (s/optional-key :incident_time) (describe IncidentTime "relevant time values associated with this Incident") ;; Was "time"; renamed for clarity
    (s/optional-key :categories) (describe [v/IncidentCategory] "a set of categories for this incident")
    (s/optional-key :reporter) (describe s/Str "information about the reporting source of this Incident")
    (s/optional-key :responder) (describe s/Str "information about the assigned responder for this Incident")
    (s/optional-key :coordinator) (describe s/Str "information about the assigned coordinator for this Incident")
    (s/optional-key :victim) (describe s/Str "information about a victim of this Incident")
    (s/optional-key :affected_assets) (describe [AffectedAsset] "particular assets affected during the Incident")
    (s/optional-key :impact_assessment) (describe ImpactAssessment "a summary assessment of impact for this cyber threat Incident")
    (s/optional-key :source) s/Str
    (s/optional-key :security_compromise)
    (describe v/SecurityCompromise "knowledge of whether the Incident involved a compromise of security properties")
    (s/optional-key :discovery_method) (describe v/DiscoveryMethod "identifies how the incident was discovered")
    (s/optional-key :COA_requested)
    (describe [COARequested] "specifies and characterizes requested Course Of Action for this Incident as specified by the Producer for the Consumer of the Incident Report")
    (s/optional-key :COA_taken) (describe [COARequested] "specifies and characterizes a Course Of Action taken for this Incident")
    (s/optional-key :contact) (describe s/Str "identifies and characterizes organizations or personnel involved in this Incident")
    (s/optional-key :history) (describe [History] "a log of events or actions taken during the handling of the Incident")

    ;; The seqs of elements below are squashed (they leave out
    ;; structured data such as confidence and source for each element).
    (s/optional-key :related_indicators)
    (describe rel/RelatedIndicators "identifies or characterizes one or more cyber threat Indicators related to this cyber threat Incident")
    (s/optional-key :related_observables)
    (describe [c/Observable] "identifies or characterizes one or more cyber observables related to this cyber threat incident") ;; Was related_observables
    (s/optional-key :leveraged_TTPs)
    (describe rel/RelatedTTPs "specifies TTPs asserted to be related to this cyber threat Incident")
    (s/optional-key :attributed_actors)
    (describe rel/RelatedActors "identifies ThreatActors asserted to be attributed for this Incident") ;; was attributed_threat_actors
    (s/optional-key :related_incidents)
    (describe rel/RelatedIncidents "identifies or characterizes one or more other Incidents related to this cyber threat Incident")
    (s/optional-key :intended_effect)
    (describe v/IntendedEffect "specifies the suspected intended effect of this incident")

    ;; Not provided: URL
    ;; Not provided: external_id
    ;; Not provided: handling
    ;; Not provided: related_packages (deprecated)
    }))

(s/defschema NewIncident
  (st/merge
   (st/dissoc Incident
              :id
              :valid_time)
   {(s/optional-key :valid_time) c/ValidTime}))

(s/defschema StoredIncident
  "An incident as stored in the data store"
  (st/merge Incident
            {:owner s/Str
             :created c/Time}))

(s/defn realize-incident :- StoredIncident
  [new-incident :- NewIncident
   id :- s/Str]
  (let [now (c/timestamp)]
    (assoc new-incident
           :id id
           :owner "not implemented"
           :created now
           :valid_time {:start_time (or (get-in new-incident [:valid_time :start_time])
                                        now)
                        :end_time (or (get-in new-incident [:valid_time :end_time])
                                      c/default-expire-date)})))
