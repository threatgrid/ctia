(ns cia.schemas.ttp
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st]))

(s/defschema AttackPattern
  "See http://stixproject.github.io/data-model/1.2/ttp/AttackPatternType/"
  {:description (describe s/Str "text description of an individual Attack Pattern")
   (s/optional-key :capec_id)
   (describe s/Str "a reference to a particular entry within the Common Attack Pattern Enumeration and Classification")})

(s/defschema MalwareInstance
  "See http://stixproject.github.io/data-model/1.2/ttp/MalwareInstanceType/"
  {:description (describe s/Str "text description of an individual Malware Instance")
   :type (describe [v/MalwareType] "a characterization of what type of malware this")
   ;; Not provided: name ; empty vocab
   })

(s/defschema Behavior
  "See http://stixproject.github.io/data-model/1.2/ttp/BehaviorType/"
  {(s/optional-key :attack_patterns) (describe [AttackPattern] "one or more Attack Patterns for this TTP")
   (s/optional-key :malware_type) (describe [MalwareInstance] "one or more instances of Malware for this TTP")
   ;; Not provided: exploits ; It is abstract
   })

(s/defschema Infrastructure
  "See http://stixproject.github.io/data-model/1.2/ttp/InfrastructureType/"
  {:description (describe s/Str "text description of specific classes or instances of infrastructure utilized for cyber attack")
   :type (describe v/AttackerInfrastructure "represents the type of infrastructure being described")
   ;; Not provided: observable_characterization ; characterization of CybOX observables
   })

(s/defschema Resource
  "See http://stixproject.github.io/data-model/1.2/ttp/ResourceType/"
  {(s/optional-key :tools) (describe [c/Tool] "one or more Tools leveraged by this TTP")
   (s/optional-key :infrastructure) (describe Infrastructure "infrastructure observed to have been utilized for cyber attack")
   (s/optional-key :providers) [c/Identity]})

(s/defschema VictimTargeting
  "See http://stixproject.github.io/data-model/1.2/ttp/VictimTargetingType/"
  {(s/optional-key :identity) (describe c/Identity "infrastructure observed to have been utilized for cyber attack")
   (s/optional-key :targeted_systems) (describe [v/SystemType] "type of system that is targeted")
   (s/optional-key :targeted_information) (describe [v/InformationType] "a type of information that is targeted")
   (s/optional-key :targeted_observables) (describe [c/Observable] "targeted observables")}) ;; Was targeted_technical_details

(s/defschema TTP
  "See http://stixproject.github.io/data-model/1.2/ttp/TTPType/"
  (merge
   c/GenericStixIdentifiers
   {:valid_time (describe c/ValidTime "a timestamp for the definition of a specific version of a TTP item")
    (s/optional-key :version)
    (describe s/Str "the relevant schema version for this content")

    (s/optional-key :intended_effect)
    (describe v/IntendedEffect "the suspected intended effect for this TTP")

    (s/optional-key :behavior)
    (describe Behavior "describes the attack patterns, malware, or exploits that the attacker leverages to execute this TTP")

    (s/optional-key :resources)
    (describe Resource "infrastructure or tools that the adversary uses to execute this TTP")

    (s/optional-key :victim_targeting)
    (describe VictimTargeting "characterizes the people, organizations, information or access being targeted")

    (s/optional-key :exploit_targets)
    (describe rel/RelatedExploitTargets "potential vulnerability, weakness or configuration targets for exploitation by this TTP")

    (s/optional-key :related_TTPs)
    (describe rel/RelatedTTPs "specifies other TTPs asserted to be related to this cyber threat TTP")

    (s/optional-key :source)
    (describe s/Str "source of this cyber threat TTP")

    ;; Extension fields:
    :type  (describe s/Str "type of this TTP")
    :indicators (describe [rel/IndicatorReference] "related indicators")
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
   {(s/optional-key :valid_time) c/ValidTime}))

(s/defschema StoredTTP
  "A TTP as stored in the data store"
  (st/merge TTP
            {:owner s/Str
             :created c/Time
             :modified c/Time}))

(s/defn realize-ttp :- StoredTTP
  ([new-ttp :- NewTTP
    id :- s/Str
    login :- s/Str]
   (realize-ttp new-ttp id login nil))
  ([new-ttp :- NewTTP
    id :- s/Str
    login :- s/Str
    prev-ttp :- (s/maybe StoredTTP)]
   (let [now (c/timestamp)]
     (assoc new-ttp
            :id id
            :owner login
            :created (or (:created prev-ttp) now)
            :modified now
            :valid_time (or (:valid_time prev-ttp)
                            {:start_time (or (get-in new-ttp [:valid_time :start_time])
                                             now)
                             :end_time (or (get-in new-ttp [:valid_time :end_time])
                                           c/default-expire-date)})))))
