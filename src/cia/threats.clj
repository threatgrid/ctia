(ns cia.threats
  (:require [schema.core :as s]
            [cia.models
             :refer [Observable Time URI Confidence Reference ID Indicator]
             :as m]
            [ring.swagger.schema :refer [coerce!]]))

;; TODO - README for each section

;;mutable
(s/defschema TTP
  "See http://stixproject.github.io/data-model/1.2/ttp/TTPType/"
  {:id ID
   :timestamp m/Time
   :title s/Str

   (s/optional-key :version) s/Str
   (s/optional-key :description) [s/Str]
   (s/optional-key :sort_description) [s/Str]
   (s/optional-key :intended_effect) m/IntendedEffect
   (s/optional-key :behavior) m/Behavior
   (s/optional-key :resources) m/Resource
   (s/optional-key :victim_targeting) m/VictimTargeting


   :source s/Str
   :type  s/Str
   :expires Time

   :description s/Str
   :short_description s/Str

   :intended_effect s/Str ;; typed

   :behavior s/Str ;;typed

   :indicators [Reference]

   ;; Not Provided: idref
   })

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
   :effidacy s/Str

   :source s/Str

   :handling s/Str
   :related_COAs [Reference]

   })

;; TODO - Record any fields that are left out, or any squashing in the code
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
    (s/optional-key :source) m/Source ;; was information_source
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
    (s/optional-key :leveraged_ttps) [m/Reference]
    (s/optional-key :attributed_actors) [m/Reference] ;; was attributed_threat_actors
    (s/optional-key :related_incidents) [m/Reference]
    (s/optional-key :intended_effect) m/IntendedEffect

    ;; Not provided: idref
    ;; Not provided: URL
    ;; Not provided: external_id
    ;; Not provided: handling
    ;; Not provided: related_packages (deprecated)
    }))

(s/defschema ExploitTarget
  ;; TODO - See if this is worth doing ;; covers Vulnerabilities
  {}
  )
