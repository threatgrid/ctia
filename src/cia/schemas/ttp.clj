(ns cia.schemas.ttp
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema AttackPattern
  "See http://stixproject.github.io/data-model/1.2/ttp/AttackPatternType/"
  {:description s/Str
   (s/optional-key :capec_id) s/Str})

(s/defschema MalwareInstance
  "See http://stixproject.github.io/data-model/1.2/ttp/MalwareInstanceType/"
  {:description s/Str
   :type [v/MalwareType]
   ;; Not provided: name ; empty vocab
   })

(s/defschema Behavior
  "See http://stixproject.github.io/data-model/1.2/ttp/BehaviorType/"
  {(s/optional-key :attack_patterns) [AttackPattern]
   (s/optional-key :malware_type) [MalwareInstance]
   ;; Not provided: exploits ; It is abstract
   })

(s/defschema Infrastructure
  "See http://stixproject.github.io/data-model/1.2/ttp/InfrastructureType/"
  {:description s/Str
   :type v/AttackerInfrastructure
   ;; Not provided: observable_characterization ; characterization of CybOX observables
   })

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
   (s/optional-key :targeted_observables) [c/Observable]}) ;; Was targeted_technical_details

(s/defschema TTP
  "See http://stixproject.github.io/data-model/1.2/ttp/TTPType/"
  (merge
   c/GenericStixIdentifiers
   {:valid_time c/ValidTime
    (s/optional-key :version) s/Str
    (s/optional-key :intended_effect) v/IntendedEffect
    (s/optional-key :behavior) Behavior
    (s/optional-key :resources) Resource
    (s/optional-key :victim_targeting) VictimTargeting
    (s/optional-key :exploit_targets) rel/RelatedExploitTargets
    (s/optional-key :related_TTPs) rel/RelatedTTPs
    (s/optional-key :source) s/Str

    ;; Extension fields:
    :type  s/Str
    :indicators [rel/IndicatorReference]

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
    id :- s/Str]
   (realize-ttp new-ttp id nil))
  ([new-ttp :- NewTTP
    id :- s/Str
    prev-ttp :- (s/maybe StoredTTP)]
   (let [now (c/timestamp)]
     (assoc new-ttp
            :id id
            :owner "not implemented"
            :created (or (:created prev-ttp) now)
            :modified now
            :valid_time (or (:valid_time prev-ttp)
                            {:start_time (or (get-in new-ttp [:valid_time :start_time])
                                             now)
                             :end_time (or (get-in new-ttp [:valid_time :end_time])
                                           c/default-expire-date)})))))
