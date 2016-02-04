(ns cia.schemas.ttp
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]))

(s/defschema AttackPattern
  "See http://stixproject.github.io/data-model/1.2/ttp/AttackPatternType/"
  (merge
   c/GenericStixIdentifiers
   {(s/optional-key :capec_id) s/Str}))

(s/defschema MalwareInstance
  "See http://stixproject.github.io/data-model/1.2/ttp/MalwareInstanceType/"
  (merge
   c/GenericStixIdentifiers
   {:type [v/MalwareType]
    ;; Not provided: name ; empty vocab
    }))

(s/defschema Behavior
  "See http://stixproject.github.io/data-model/1.2/ttp/BehaviorType/"
  {(s/optional-key :attack_patterns) [AttackPattern]
   (s/optional-key :malware_type) [MalwareInstance]
   ;; Not provided: exploits ; It is abstract
   })

(s/defschema Infrastructure
  "See http://stixproject.github.io/data-model/1.2/ttp/InfrastructureType/"
  (merge
   c/GenericStixIdentifiers
   {:type v/AttackerInfrastructure
    ;; Not provided: observable_characterization ; characterization of CybOX observables
    }))

(s/defschema Resource
  "See http://stixproject.github.io/data-model/1.2/ttp/ResourceType/"
  {(s/optional-key :tools) [c/Tool]
   (s/optional-key :infrastructure) Infrastructure
   (s/optional-key :providers) [c/Identity]})

(s/defschema VictimTargeting
  "See http://stixproject.github.io/data-model/1.2/ttp/VictimTargetingType/"
  {(s/optional-key :identity) c/Identity
   (s/optional-key :targeted_systems) [v/SystemType]
   (s/optional-key :targeted_information) [v/InformationType]
   ;; Not provided: targeted_technical_details ; Points to ObservablesType
   })

(s/defschema TTP
  "See http://stixproject.github.io/data-model/1.2/ttp/TTPType/"
  (merge
   c/GenericStixIdentifiers
   {:timestamp c/Time
    (s/optional-key :version) s/Str
    (s/optional-key :intended_effect) v/IntendedEffect
    (s/optional-key :behavior) Behavior
    (s/optional-key :resources) Resource
    (s/optional-key :victim_targeting) VictimTargeting
    (s/optional-key :exploit_targets) rel/RelatedExploitTargets
    (s/optional-key :related_TTPs) rel/RelatedTTPs
    (s/optional-key :source) c/Source

    ;; Extension fields:
    :type  s/Str
    :expires c/Time
    :indicators [rel/IndicatorReference]

    ;; Not provided: kill_chain_phases
    ;; Not provided: kill_chains
    ;; Not provided: handling
    ;; Not provided: related_packages (deprecated)
    }))
