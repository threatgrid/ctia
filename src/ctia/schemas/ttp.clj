(ns ctia.schemas.ttp
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st]))

(s/defschema AttackPattern
  "See http://stixproject.github.io/data-model/1.2/ttp/AttackPatternType/"
  {:description (describe s/Str
                          "text description of an individual Attack Pattern")

   (s/optional-key
    :capec_id) (describe
                s/Str
                (str "a reference to a particular entry within the Common Attack"
                     " Pattern Enumeration and Classification"))})

(s/defschema MalwareInstance
  "See http://stixproject.github.io/data-model/1.2/ttp/MalwareInstanceType/"
  {:description (describe s/Str
                          "text description of an individual Malware Instance")

   :type (describe [v/MalwareType]
                   "a characterization of what type of malware this")

   ;; Not provided: name ; empty vocab
   })

(s/defschema Behavior
  "See http://stixproject.github.io/data-model/1.2/ttp/BehaviorType/"
  (st/optional-keys
   {:attack_patterns (describe [AttackPattern]
                                 "one or more Attack Patterns for this TTP")

    :malware_type (describe [MalwareInstance]
                              "one or more instances of Malware for this TTP")
    ;; Not provided: exploits ; It is abstract
    }))

(s/defschema Infrastructure
  "See http://stixproject.github.io/data-model/1.2/ttp/InfrastructureType/"
  {:description (describe
                 s/Str
                 (str "text description of specific classes or instances of"
                      " infrastructure utilized for cyber attack"))
   :type (describe v/AttackerInfrastructure
                   "represents the type of infrastructure being described")
   ;; Not provided: observable_characterization ; characterization of CybOX observables
   })

(s/defschema Resource
  "See http://stixproject.github.io/data-model/1.2/ttp/ResourceType/"
  (st/optional-keys
   {:tools (describe [c/Tool] "one or more Tools leveraged by this TTP")
    :infrastructure (describe
                     Infrastructure
                     "infrastructure observed to have been utilized for cyber attack")
    :providers [c/Identity]}))

(s/defschema VictimTargeting
  "See http://stixproject.github.io/data-model/1.2/ttp/VictimTargetingType/"
  (st/optional-keys
   {:identity (describe
               c/Identity
               "infrastructure observed to have been utilized for cyber attack")
    :targeted_systems (describe [v/SystemType] "type of system that is targeted")
    :targeted_information (describe [v/InformationType]
                                    "a type of information that is targeted")
    :targeted_observables (describe [c/Observable] "targeted observables")})) ;; Was targeted_technical_details

(s/defschema TTP
  "See http://stixproject.github.io/data-model/1.2/ttp/TTPType/"
  (merge
   c/GenericStixIdentifiers
   {:tlp c/TLP
    :valid_time (describe
                 c/ValidTime
                 "a timestamp for the definition of a specific version of a TTP item")}
   (st/optional-keys
    {:version (describe s/Str "the relevant schema version for this content")
     :intended_effect (describe
                       v/IntendedEffect
                       "the suspected intended effect for this TTP")
     :behavior (describe
                Behavior
                (str "describes the attack patterns, malware, or exploits that"
                     " the attacker leverages to execute this TTP"))
     :resources (describe
                 Resource
                 "infrastructure or tools that the adversary uses to execute this TTP")
     :victim_targeting (describe VictimTargeting
                                 (str "characterizes the people, organizations,"
                                      " information or access being targeted"))
     :exploit_targets (describe
                       rel/RelatedExploitTargets
                       (str "potential vulnerability, weakness or configuration"
                            " targets for exploitation by this TTP"))
     :related_TTPs (describe
                    rel/RelatedTTPs
                    (str
                     "specifies other TTPs asserted to be related to this cyber"
                     " threat TTP"))
     :source (describe s/Str "source of this cyber threat TTP")})
   {;; Extension fields:
    :ttp_type (describe s/Str "type of this TTP")
    :indicators (describe rel/RelatedIndicators "related indicators")
    ;; Not provided: kill_chain_phases
    ;; Not provided: kill_chains
    ;; Not provided: handling
    ;; Not provided: related_packages (deprecated)
    }))

(s/defschema NewTTP
  (st/merge
   (st/dissoc TTP
              :id
              :valid_time)
   (st/optional-keys
    {:valid_time c/ValidTime
     :type (s/enum "ttp")
     :tlp c/TLP})))

(s/defschema StoredTTP
  "An ttp as stored in the data store"
  (c/stored-schema "ttp" TTP))

(def realize-ttp
  (c/default-realize-fn "ttp" NewTTP StoredTTP))
