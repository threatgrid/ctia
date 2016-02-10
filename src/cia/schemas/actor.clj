(ns cia.schemas.actor
  (:require [clj-time.format :as time-format]
            [clj-time.core :as time]
            [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(def default-expires-in-days 7)

(s/defschema Actor
  "http://stixproject.github.io/data-model/1.2/ta/ThreatActorType/"
  (merge
   c/GenericStixIdentifiers
   {:timestamp c/Time
    :type v/ThreatActorType
    (s/optional-key :source) c/Source
    (s/optional-key :identity) c/Identity
    (s/optional-key :motivation) v/Motivation
    (s/optional-key :sophistication) v/Sophistication
    (s/optional-key :intended_effect) v/IntendedEffect
    (s/optional-key :planning_and_operational_support) s/Str ; Empty vocab
    (s/optional-key :observed_TTPs) rel/RelatedTTPs
    (s/optional-key :associated_campaigns) rel/AssociatedCampaigns
    (s/optional-key :associated_actors) rel/AssociatedActors
    (s/optional-key :confidence) v/HighMedLow

    ;; Extension fields:
    :expires c/Time

    ;; Not provided: handling
    ;; Not provided: related_packages (deprecated)
    }))

(s/defschema NewActor
  "Schema or submitting new Actors"
  (st/merge
   (st/dissoc Actor
              :id
              :timestamp
              :expires)
   {(s/optional-key :expires) s/Str}))

(s/defn realize-actor :- Actor
  [new-actor :- NewActor
   id :- s/Str]
  (let [now (time/now)
        expires (if-let [expire-str (get new-actor :expires)]
                  (time-format/parse (time-format/formatters :date-time) expire-str)
                  (time/plus now (time/days default-expires-in-days)))]
    (assoc new-actor
           :id id
           :timestamp now
           :expires expires)))
