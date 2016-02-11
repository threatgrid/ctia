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
  "Schema for submitting new Actors"
  (st/merge
   (st/dissoc Actor
              :id
              :expires
              :timestamp)
   {(s/optional-key :expires) s/Str
    (s/optional-key :timestamp) s/Str}))

(s/defn realize-actor :- Actor
  [new-actor :- NewActor
   id :- s/Str]
  (assoc new-actor
         :id id
         :timestamp (c/timestamp (:timestamp new-actor))
         :expires (if-let [expire-str (:expires new-actor)]
                    (c/expire-on expire-str)
                    (c/expire-after))))
