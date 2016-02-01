(ns ^{:doc "STIX project data structures converted to Prismatic schemas

            How to convert the schemas:
            * Value practical and concrete over extensible and abstract
            * Flatten and simplify when possible, especially when a STIX field
              contains a data structre that is a wrapper around a defined
              vocabulary without adding important fields in the wrapping
              data structure.  In that case, avoid the wapper and use an enum
              for the vocabulary.
            * Record any omitted fields in comments at the bottom of each
              structure.
            * Note which fields are added by us (not part of STIX).
            * Prefer references to nested data structures.  Use named
              references we can tell what the reference points at.
            * References should point to stored object types.
            * Use capital letters in identifiers and keywords for acronyms.
            * Fields should be renamed to their acronyms when we implement the
              referenced structure.
            * Rename fields to shorten *smurfy* names (eg threat_actor -> actor)
            * Rename common fields:
               information_source -> source"}
    cia.threats
    (:require [schema.core :as s]
              [cia.models
               :refer [Observable Time URI Confidence Reference ID Indicator]
               :as m]
              [ring.swagger.schema :refer [coerce!]]))

;; TODO - README for each section
;; TODO - Determine which fields should be required/optional

;; References
(def ActorReference m/Reference)
(def CampaignReference m/Reference)
(def COAReference m/Reference)
(def ExploitTargetReference m/Reference)
(def IncidentReference m/Reference)
(def TTPReference m/Reference)


;; Tools, Techniques, & Processes (TTP)

(s/defschema RelatedTTP
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedTTPType/"
  (merge
   m/RelatedWrapper
   {:TTP TTPReference}))

(s/defschema RelatedTTPs
  "See http://stixproject.github.io/data-model/1.2/ttp/RelatedTTPsType/"
  (merge
   m/ScopeWrapper
   {:related_TTPs [RelatedTTP]}))

(s/defschema ObservedTTPs
  "See http://stixproject.github.io/data-model/1.2/ta/ObservedTTPsType/"
  (merge
   m/ScopeWrapper
   {:observed_TTPs [RelatedTTP]}))

(s/defschema LeveragedTTPs
  "See http://stixproject.github.io/data-model/1.2/incident/LeveragedTTPsType/"
  (merge
   m/ScopeWrapper
   {:levereged_TTPs [RelatedTTP]}))

(s/defschema TTP
  "See http://stixproject.github.io/data-model/1.2/ttp/TTPType/"
  (merge
   m/GenericStixIdentifiers
   {:timestamp m/Time
    (s/optional-key :version) s/Str
    (s/optional-key :intended_effect) m/IntendedEffect
    (s/optional-key :behavior) m/Behavior
    (s/optional-key :resources) m/Resource
    (s/optional-key :victim_targeting) m/VictimTargeting
    (s/optional-key :exploit_targets) [RelatedExploitTarget]
    (s/optional-key :related_TTPs) RelatedTTPs
    (s/optional-key :source) m/Source

    ;; Extension fields:
    :type  s/Str
    :expires Time
    :indicators [m/IndicatorReference]

    ;; Not provided: kill_chain_phases
    ;; Not provided: kill_chains
    ;; Not provided: handling
    ;; Not provided: related_packages (deprecated)
    }))


;; Actor

(s/defschema RelatedActor
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedThreatActorType/"
  (merge
   m/RelatedWrapper
   {:actor [ActorReference]}))

(s/defschema AssociatedActors
  "See http://stixproject.github.io/data-model/1.2/ta/AssociatedActorsType/"
  (merge
   m/ScopeWrapper
   {:associated_actors [RelatedActor]}))

(s/defschema AttributedActors
  "See http://stixproject.github.io/data-model/1.2/incident/AttributedThreatActorsType/"
  (merge
   m/ScopeWrapper
   {:attributed_actors [RelatedActor]}))

(s/defschema Actor
  "http://stixproject.github.io/data-model/1.2/ta/ThreatActorType/"
  (merge
   m/GenericStixIdentifiers
   {:timestamp Time
    (s/optional-key :source) m/Source
    (s/optional-key :identity) m/Identity
    :type m/ThreatActorType
    (s/optional-key :motivation) m/Motivation
    (s/optional-key :sophistication) m/Sophistication
    (s/optional-key :intended_effect) m/IntendedEffect
    (s/optional-key :planning_and_operational_support) s/Str ; Empty vocab
    (s/optional-key :observed_TTPs) RelatedTTPs
    (s/optional-key :associated_campaigns) AssociatedCampaigns
    (s/optional-key :associated_actors) AssociatedActors
    (s/optional-key :confidence) m/Confidence

    ;; Extension fields:
    :expires Time

    ;; Not provided: handling
    ;; Not provided: related_packages (deprecated)
    }))


;; Campaign

(s/defschema RelatedCampaign
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedCampaignType/"
  (s/merge
   m/RelatedWrapper
   {:campaigns CampaignReference}))

(s/defschema AssociatedCampaigns
  "See http://stixproject.github.io/data-model/1.2/ta/AssociatedCampaignsType/"
  (merge
   m/ScopeWrapper
   {:associated_campaigns [RelatedCampaign]}))

(s/defschema Campaign
  "See http://stixproject.github.io/data-model/1.2/campaign/CampaignType/"
  (merge
   m/GeneralStixIdentifiers
   {:timestamp Time
    (s/optional-key :version) s/Str
    (s/optional-key :names) [s/Str]
    (s/optional-key :intended_effect) [m/IntendedEffect]
    (s/optional-key :status) m/CampaignStatus
    (s/optional-key :related_TTPs) RelatedTTPs
    (s/optional-key :related_incidents) RelatedIncidents
    (s/optional-key :attribution) AttributedActors
    (s/optional-key :associated_campaigns) AssociatedCampaigns
    (s/optional-key :confidence) m/Confidence
    (s/optional-key :activity) m/Activity
    (s/optional-key :source) m/Source

    ;; Extension fields:
    :type  s/Str
    :expires m/Time
    :indicators [m/Reference] ;; TODO - Specify reference type

    ;; Not provided: Handling
    ;; Not provided: related_packages (deprecated)
    }))


;; Course of Action (COA)

(s/defschema RelatedCOA
  "See http://stixproject.github.data-model/1.2/stixCommon/RelatedCourseOfActionType/"
  (merge
   m/RelatedWrapper
   {(s/optional-key :COA) COAReference}))

(s/defschema PotentialCOAs
  "See http://stixproject.github.io/data-model/1.2/et/PotentialCOAsType/"
  (merge
   m/ScopeWrapper
   {:potential_COAs [RelatedCOA]}))

(s/defschema COA
  {:id ID
   :title s/Str
   :stage s/Str ;;fixed vocab
   :type s/Str
   :short_description s/Str
   :description s/Str

   :objective [s/Str]

   :impact s/Str
   :cost s/Str
   :efficacy s/Str

   :source s/Str

   :handling s/Str
   :related_COAs [PotentialCOA]

   })

;; Incident

;; TODO - Write up transforms and variations in the README, include notes for serialization
;; TODO - Add NewIncident and StoredIncident

(s/defschema RelatedIncident
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedIncidentType/"
  (merge
   RelatedWrapper
   {:incident IncidentReference}))

(s/defschema RelatedIncidents
  "See http://stixproject.github.io/data-model/1.2/campaign/RelatedIncidentsType/"
  (merge
   m/ScopeWrapper
   {:related_incidents [RelatedIncident]}))

(s/defschema COARequested
  "See http://stixproject.github.io/data-model/1.2/incident/COARequestedType/
   and http://stixproject.github.io/data-model/1.2/incident/COATakenType/"
  {(s/optional-key :time) m/Time
   (s/optional-key :contributors) [m/Contributor]
   :COA COAReference})

(s/defschema Incident
  "See http://stixproject.github.io/data-model/1.2/incident/IncidentType/"
  (merge
   m/GenericStixIdentifiers
   {:timestamp m/Time ;; timestamp "for this version"; optional in spec
    :confidence m/Confidence ;; squashed; TODO - Consider expanding

    (s/optional-key :status) m/Status
    (s/optional-key :version) s/Str
    (s/optional-key :incident_time) m/IncidentTime ;; Was "time"; renamed for clarity
    (s/optional-key :categories) [m/IncidentCategory]
    (s/optional-key :reporter) m/Source
    (s/optional-key :responder) m/Source
    (s/optional-key :coordinator) m/Source
    (s/optional-key :victim) s/Str
    (s/optional-key :affected_assets) m/AffectedAsset
    (s/optional-key :impact_assessment) m/ImpactAssessment
    (s/optional-key :source) m/Source
    (s/optional-key :security_compromise) m/SecurityCompromise
    (s/optional-key :discovery_method) m/DiscoveryMethod
    (s/optional-key :coa_requested) [m/COARequested]
    (s/optional-key :coa_taken) [m/COARequested]
    (s/optional-key :contact) m/Source
    (s/optional-key :history) m/History

    ;; The seqs of elements below are squashed (they leave out
    ;; structured data such as confidence and source for each element).
    (s/optional-key :related_indicators) [RelatedIndicators]
    (s/optional-key :related_observables) [m/Reference] ;; TODO - Specify the reference type
    (s/optional-key :leveraged_TTPs) [LeveragedTTPs]
    (s/optional-key :attributed_actors) [AttributedActors] ;; was attributed_threat_actors
    (s/optional-key :related_incidents) [RelatedIncidents]
    (s/optional-key :intended_effect) m/IntendedEffect

    ;; Not provided: URL
    ;; Not provided: external_id
    ;; Not provided: handling
    ;; Not provided: related_packages (deprecated)
    }))


;; Exploit Target

(s/defschema RelatedExploitTarget
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedExploitTargetType/"
  (merge
   m/RelatedWrapper
   {:exploit_target  ExploitTargetReference}))

(s/defschema ExploitTargets
  "See http://stixproject.github.io/data-model/1.2/ttp/ExploitTargetsType/"
  {(s/optional-key :scope) m/Scope
   :exploit_targets [RelatedExploitTarget]})

(s/defschema ExploitTarget
  "See http://stixproject.github.io/data-model/1.2/et/ExploitTargetType/"
  (merge
   m/GenericStixIdentifiers
   {:timestamp m/Time
    :version s/Str
    :vulnerability [m/Vulnerability]
    :weakness [m/Weakness]
    :configuration [m/Configuration]
    :potential_COAs PotentialCOAs
    :source m/Source
    :related_exploit_targets RelatedExploitTargets
    ;; Not provided: related_packages (deprecated)
    ;; Not provided: handling
    }))
