(ns cia.schemas.actor
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema Actor
  "http://stixproject.github.io/data-model/1.2/ta/ThreatActorType/"
  (merge
   c/GenericStixIdentifiers
   {:valid_time c/ValidTime
    :type v/ThreatActorType
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

(s/defschema NewActor
  "Schema for submitting new Actors"
  (st/merge
   (st/dissoc Actor
              :id
              :valid_time)
   {(s/optional-key :valid_time) c/ValidTime}))

(s/defschema StoredActor
  "An actor as stored in the data store"
  (st/merge Actor
            {:owner s/Str
             :created c/Time
             :modified c/Time}))

(s/defn realize-actor :- StoredActor
  [new-actor :- NewActor
   id :- s/Str]
  (let [now (c/timestamp)]
    (assoc new-actor
           :id id
           :owner "not implemented"
           :created now
           :modified now
           :valid_time {:end_time (or (get-in new-actor [:valid_time :end_time])
                                      c/default-expire-date)
                        :start_time (or (get-in new-actor [:valid_time :start_time])
                                        now)})))
(s/defn replace-actor :- StoredActor
  [new-actor :- NewActor
   id :- s/Str
   old-actor :- StoredActor]
  (assoc new-actor
         :id (:id old-actor)
         :owner "not implemented"
         :created (:created old-actor)
         :modified (c/timestamp)))
