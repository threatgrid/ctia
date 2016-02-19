(ns cia.schemas.incident
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema COARequested
  "See http://stixproject.github.io/data-model/1.2/incident/COARequestedType/
   and http://stixproject.github.io/data-model/1.2/incident/COATakenType/"
  {(s/optional-key :time) c/Time
   (s/optional-key :contributors) [c/Contributor]
   :COA rel/COAReference})

(s/defschema NonPublicDataCompromised
  "See http://stixproject.github.io/data-model/1.2/incident/NonPublicDataCompromisedType/"
  ;; Simplified schema
  {:security_compromise v/SecurityCompromise
   (s/optional-key :data_encrypted) s/Bool})

(s/defschema PropertyAffected
  "See http://stixproject.github.io/data-model/1.2/incident/PropertyAffectedType/"
  {(s/optional-key :property) v/LossProperty
   (s/optional-key :description_of_effect) s/Str
   (s/optional-key :type_of_availability_loss) s/Str ;; Vocab is empty
   (s/optional-key :duration_of_availability_loss) v/LossDuration
   (s/optional-key :non_public_data_compromised) NonPublicDataCompromised})

(s/defschema AffectedAsset
  "See http://stixproject.github.io/data-model/1.2/incident/AffectedAssetType/"
  {(s/optional-key :type) s/Str
   (s/optional-key :description) s/Str
   (s/optional-key :ownership_class) v/OwnershipClass
   (s/optional-key :management_class) v/ManagementClass
   (s/optional-key :location_class) v/LocationClass
   (s/optional-key :property_affected) PropertyAffected ;; Unnested NatureOfSecurityEffect
   (s/optional-key :identifying_observables) [c/Observable]
   ;; Not Provided: business_function_or_role
   ;; Not Provided: location (undefined/abstract type); Could be [s/Str]
   })

(s/defschema DirectImpactSummary
  "See http://stixproject.github.io/data-model/1.2/incident/DirectImpactSummaryType/"
  {(s/optional-key :asset_losses) v/ImpactRating
   (s/optional-key :business_mission_distruption) v/ImpactRating
   (s/optional-key :response_and_recovery_costs) v/ImpactRating})

(s/defschema IndirectImpactSummary
  "See http://stixproject.github.io/data-model/1.2/incident/IndirectImpactSummaryType/"
  {(s/optional-key :loss_of_competitive_advantage) v/SecurityCompromise
   (s/optional-key :brand_and_market_damage) v/SecurityCompromise
   (s/optional-key :increased_operating_costs) v/SecurityCompromise
   (s/optional-key :local_and_regulatory_costs) v/SecurityCompromise})

(s/defschema LossEstimation
  "See http://stixproject.github.io/data-model/1.2/incident/LossEstimationType/"
  {(s/optional-key :amount) s/Num
   (s/optional-key :iso_currency_code) s/Str})

(s/defschema TotalLossEstimation
  "See http://stixproject.github.io/data-model/1.2/incident/TotalLossEstimationType/"
  {(s/optional-key :initial_reported_total_loss_estimation) LossEstimation
   (s/optional-key :actual_total_loss_estimation) LossEstimation})

(s/defschema ImpactAssessment
  "See http://stixproject.github.io/data-model/1.2/incident/ImpactAssessmentType/"
  {(s/optional-key :direct_impact_summary) DirectImpactSummary
   (s/optional-key :indirect_impact_summary) IndirectImpactSummary
   (s/optional-key :total_loss_estimation) TotalLossEstimation
   (s/optional-key :impact_qualification) v/ImpactQualification
   (s/optional-key :effects) [v/Effect]
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
  {(s/optional-key :action_entry) [COARequested]
   (s/optional-key :journal_entry) s/Str ;; simplified
   })

(s/defschema Incident
  "See http://stixproject.github.io/data-model/1.2/incident/IncidentType/"
  (st/merge
   c/GenericStixIdentifiers
   {:valid_time c/ValidTime
    :confidence v/HighMedLow
    (s/optional-key :status) v/Status
    (s/optional-key :version) s/Str
    (s/optional-key :incident_time) IncidentTime ;; Was "time"; renamed for clarity
    (s/optional-key :categories) [v/IncidentCategory]
    (s/optional-key :reporter) s/Str
    (s/optional-key :responder) s/Str
    (s/optional-key :coordinator) s/Str
    (s/optional-key :victim) s/Str
    (s/optional-key :affected_assets) [AffectedAsset]
    (s/optional-key :impact_assessment) ImpactAssessment
    (s/optional-key :source) s/Str
    (s/optional-key :security_compromise) v/SecurityCompromise
    (s/optional-key :discovery_method) v/DiscoveryMethod
    (s/optional-key :COA_requested) [COARequested]
    (s/optional-key :COA_taken) [COARequested]
    (s/optional-key :contact) s/Str
    (s/optional-key :history) [History]

    ;; The seqs of elements below are squashed (they leave out
    ;; structured data such as confidence and source for each element).
    (s/optional-key :related_indicators) rel/RelatedIndicators
    (s/optional-key :related_observables) [c/Observable] ;; Was related_observables
    (s/optional-key :leveraged_TTPs) rel/RelatedTTPs
    (s/optional-key :attributed_actors) rel/RelatedActors ;; was attributed_threat_actors
    (s/optional-key :related_incidents) rel/RelatedIncidents
    (s/optional-key :intended_effect) v/IntendedEffect

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
