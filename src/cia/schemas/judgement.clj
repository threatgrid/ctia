(ns cia.schemas.judgement
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(def Severity s/Int)

(def Priority
  "A value 0-100 that determiend the priority of a judgement.  Curated
  feeds of black/whitelists, for example known good products within
  your organizations, should use a 95. All automated systems should
  use a priority of 90, or less.  Human judgements should have a
  priority of 100, so that humans can always override machines."
  s/Int)

(s/defschema Judgement
  "A judgement about the intent or nature of an Observable.  For
  example, is it malicious, meaning is is malware and subverts system
  operations.  It could also be clean and be from a known benign, or
  trusted source.  It could also be common, something so widespread
  that it's not likely to be malicious."
  {:id c/ID
   :observable c/Observable
   :disposition c/DispositionNumber
   :disposition_name c/DispositionName
   :source s/Str
   :priority Priority
   :confidence v/HighMedLow
   :severity Severity
   :valid_time c/ValidTime
   (s/optional-key :reason) s/Str
   (s/optional-key :source_uri) c/URI
   (s/optional-key :reason_uri) c/URI
   (s/optional-key :indicators) rel/RelatedIndicators})

(s/defschema NewJudgement
  "Schema for submitting new Judgements."
  (st/merge
   (st/dissoc Judgement
              :id
              :disposition
              :disposition_name
              :valid_time)
   {(s/optional-key :disposition) c/DispositionNumber
    (s/optional-key :disposition_name) c/DispositionName
    (s/optional-key :valid_time) c/ValidTime}))

(s/defschema StoredJudgement
  "A judgement as stored in the data store"
  (st/merge Judgement
            {:owner s/Str
             :created c/Time}))

(s/defn realize-judgement :- StoredJudgement
  [new-judgement :- NewJudgement
   id :- s/Str]
  (let [now (c/timestamp)
        disposition (c/determine-disposition-id new-judgement)
        disposition_name (get c/disposition-map disposition)]
    (assoc new-judgement
           :id id
           :disposition disposition
           :disposition_name disposition_name
           :owner "not implemented"
           :created now
           :valid_time {:end_time (or (get-in new-judgement [:valid_time :end-time])
                                      c/default-expire-date)
                        :start_time (or (get-in new-judgement [:valid_time :start_time])
                                        now)})))
