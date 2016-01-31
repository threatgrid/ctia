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
            * Use capital letters in identifiers and keywords for acronyms.
            * Rename common fields:
               information_source -> source"}
    cia.threats
    (:require [schema.core :as s]
              [cia.models
               :refer [Observable Time URI Confidence Reference ID Indicator]
               :as m]
              [ring.swagger.schema :refer [coerce!]]))

;; TODO - README for each section
;; TODO - Determine which fields should be required

;; References
(def COAReference m/Reference)
(def ExploitTargetReference m/Reference)
(def ExploitTargetsReference m/Reference)
(def PotentialCOAReference m/Reference)
(def RelatedExploitTargetReference m/Reference)
(def RelatedTTPsReference m/Reference)
(def TTPReference m/Reference)

;; Tools, Techniques, & Processes (TTP)

(s/defschema RelatedTTP
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedTTPType/"
  (merge
   m/ScopeWrapper
   {(s/optional-key :confidence) m/Confidence
    (s/optional-key :relationship) s/Str
    :TTP TTPReference}))

(s/defschema RelatedTTPs
  "See http://stixproject.github.io/data-model/1.2/ttp/RelatedTTPsType/"
  (merge
   m/ScopeWrapper
   {:related_TTP [RelatedTTP]}))

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
    (s/optional-key :exploit_targets) [RelatedExploitTargetReference]
    (s/optional-key :related_TTPs) RelatedTTPsReference
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

;;mutable
(s/defschema Actor
  "http://stixproject.github.io/data-model/1.2/ta/ThreatActorType/"
  {:id ID
   :title s/Str
   :source s/Str
   :type  s/Str
   :timestamp Time
   :expires Time

   :description s/Str
   :short_description s/Str
   })

;;mutable
(s/defschema Campaign
  "See http://stixproject.github.io/data-model/1.2/campaign/CampaignType/"
  {:id ID
   :title s/Str
   :source s/Str
   :type  s/Str
   :timestamp Time
   :expires Time

   :description s/Str
   :short_description s/Str

   :indicators [Reference]

   })

;; Course of Action (COA)

(s/defschema RelatedCOA
  "See http://stixproject.github.data-model/1.2/stixCommon/RelatedCourseOfActionType/"
  {(s/optional-key :confidence) m/Confidence
   (s/optional-key :source) m/Source
   (s/optional-key :relationship) s/Str
   (s/optional-key :course_of_action) COAReference})

(s/defschema PotentialCOA
  "See http://stixproject.github.io/data-model/1.2/et/PotentialCOAsType/"
  (merge
   m/ScopeWrapper
   {:potential_COA [RelatedCOA]}))

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
   :related_COAs [PotentialCOAReference]

   })

;; TODO - Write up transforms and variations in the README, include notes for serialization
;; TODO - Add NewIncident and StoredIncident
(s/defschema Incident
  "See http://stixproject.github.io/data-model/1.2/incident/IncidentType/"
  (merge
   m/GenericStixIdentifiers
   {:timestamp m/Time ;; timestamp "for this version"; optional in spec
    :confidence m/Confidence ;; squashed

    (s/optional-key :status) m/Status
    (s/optional-key :version) s/Str
    (s/optional-key :time) m/IncidentTime
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
    (s/optional-key :coa_requested) [m/Reference]
    (s/optional-key :coa_taken) [m/Reference]
    (s/optional-key :contact) m/Source
    (s/optional-key :history) m/History

    ;; The seqs of elements below are squashed (they leave out
    ;; structured data such as confidence and source for each element).
    (s/optional-key :related_indicators) [m/Reference]
    (s/optional-key :related_observables) [m/Reference]
    (s/optional-key :leveraged_TTPs) [m/Reference]
    (s/optional-key :attributed_actors) [m/Reference] ;; was attributed_threat_actors
    (s/optional-key :related_incidents) [m/Reference]
    (s/optional-key :intended_effect) m/IntendedEffect

    ;; Not provided: URL
    ;; Not provided: external_id
    ;; Not provided: handling
    ;; Not provided: related_packages (deprecated)
    }))

;; Exploit Target

;; TODO - Is this too much information?  This is a wrapper around ExploitTarget.
;;        Could we just live without it?
(s/defschema RelatedExploitTarget
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedExploitTargetType/"
  {(s/optional-key :confidence) m/Confidence
   (s/optional-key :source) m/Source
   (s/optional-key :relationship) s/Str
   :exploit_target  ExploitTargetReference
   })

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
    :potential_COAs PotentialCOAReference
    :source m/Source
    :related_exploit_targets RelatedExploitTargetReference
    ;; Not provided: related_packages (deprecated)
    ;; Not provided: handling
    }))
