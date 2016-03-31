(ns ctia.schemas.actor
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema Actor
  "http://stixproject.github.io/data-model/1.2/ta/ThreatActorType/"
  (merge
   c/GenericStixIdentifiers
   {:valid_time c/ValidTime
    :actor_type v/ThreatActorType
    (s/optional-key :source) s/Str
    (s/optional-key :identity) c/Identity
    (s/optional-key :motivation) v/Motivation
    (s/optional-key :sophistication) v/Sophistication
    (s/optional-key :intended_effect) v/IntendedEffect
    (s/optional-key :planning_and_operational_support) s/Str ; Empty vocab
    (s/optional-key :observed_TTPs) rel/RelatedTTPs
    (s/optional-key :associated_campaigns) rel/RelatedCampaigns
    (s/optional-key :associated_actors) rel/RelatedActors
    (s/optional-key :confidence) v/HighMedLow

    ;; Not provided: handling
    ;; Not provided: related_packages (deprecated)
    }))

(s/defschema Type
  (s/eq "actor"))

(s/defschema NewActor
  "Schema for submitting new Actors"
  (st/merge
   (st/dissoc Actor
              :id
              :valid_time)
   {(s/optional-key :valid_time) c/ValidTime
    (s/optional-key :type) Type}))

(s/defschema StoredActor
  "An actor as stored in the data store"
  (st/merge Actor
            {:type Type
             :owner s/Str
             :created c/Time
             :modified c/Time}))

(s/defn realize-actor :- StoredActor
  ([new-actor :- NewActor
    id :- s/Str
    login :- s/Str]
   (realize-actor new-actor id login nil))
  ([new-actor :- NewActor
    id :- s/Str
    login :- s/Str
    prev-actor :- (s/maybe StoredActor)]
   (let [now (time/now)]
     (assoc new-actor
            :id id
            :type "actor"
            :owner login
            :created (or (:created prev-actor) now)
            :modified now
            :valid_time (or (:valid_time prev-actor)
                            {:end_time (or (get-in new-actor [:valid_time :end_time])
                                           c/default-expire-date)
                             :start_time (or (get-in new-actor [:valid_time :start_time])
                                             now)})))))
