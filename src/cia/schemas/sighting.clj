(ns cia.schemas.sighting
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema Sighting
  "See http://stixproject.github.io/data-model/1.2/indicator/SightingType/"
  {:id c/ID
   :timestamp c/Time
   :description s/Str
   (s/optional-key :source) s/Str
   (s/optional-key :reference) c/URI
   (s/optional-key :confidence) v/HighMedLow
   (s/optional-key :related_judgements) rel/RelatedJudgements})

(s/defschema NewSighting
  (st/merge
   (st/dissoc Sighting
              :id)))

(s/defschema StoredSighting
  (st/merge Sighting
            {:owner s/Str
             :created c/Time
             :modified c/Time}))

(s/defn realize-sighting :- StoredSighting
  ([new-sighting :- NewSighting
    id :- s/Str
    login :- s/Str]
   (realize-sighting new-sighting id login nil))
  ([new-sighting :- NewSighting
    id :- s/Str
    login :- s/Str
    prev-sighting :- (s/maybe StoredSighting)]
   (let [now (c/timestamp)]
     (assoc new-sighting
            :id id
            :owner login
            :created (or (:created prev-sighting) now)
            :modified now))))
