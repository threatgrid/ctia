(ns ctia.schemas.sighting
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.vocabularies :as v]
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
   (s/optional-key :related_judgements) rel/RelatedJudgements
   (s/optional-key :indicator) rel/RelatedIndicator})

(s/defschema Type
  (s/eq "sighting"))

(s/defschema NewSighting
  (st/merge
   (st/dissoc Sighting
              :id)
   {(s/optional-key :type) Type}))

(s/defschema StoredSighting
  "An sighting as stored in the data store"
  (c/stored-schema "sighting" Sighting))

(s/defn realize-sighting :- StoredSighting
  ([new-sighting :- NewSighting
    id :- s/Str
    login :- s/Str]
   (realize-sighting new-sighting id login nil))
  ([new-sighting :- NewSighting
    id :- s/Str
    login :- s/Str
    prev-sighting :- (s/maybe StoredSighting)]
   (let [now (time/now)]
     (assoc new-sighting
            :id id
            :type "sighting"
            :owner login
            :created (or (:created prev-sighting) now)
            :modified now))))
