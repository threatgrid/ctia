(ns ctia.schemas.judgement
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.vocabularies :as v]
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
  (st/merge
   {:id c/ID
    :observable c/Observable
    :disposition c/DispositionNumber
    :disposition_name c/DispositionName
    :source s/Str
    :priority Priority
    :confidence v/HighMedLow
    :severity Severity
    :valid_time c/ValidTime}
   (st/optional-keys
    {:reason s/Str
     :source_uri c/URI
     :reason_uri c/URI
     :indicators rel/RelatedIndicators})))

(s/defschema Type
  (s/enum "judgement"))

(s/defschema NewJudgement
  "Schema for submitting new Judgements."
  (st/merge
   (st/dissoc Judgement
              :id
              :disposition
              :disposition_name
              :valid_time)
   (st/optional-keys
    {:disposition c/DispositionNumber
     :disposition_name c/DispositionName
     :valid_time c/ValidTime
     :type Type})))

(s/defschema StoredJudgement
  "A judgement as stored in the data store"
  (st/merge Judgement
            {:type Type
             :owner s/Str
             :created c/Time}))

(s/defn realize-judgement :- StoredJudgement
  [new-judgement :- NewJudgement
   id :- s/Str
   login :- s/Str]
  (let [now (time/now)
        disposition (c/determine-disposition-id new-judgement)
        disposition_name (get c/disposition-map disposition)]
    (assoc new-judgement
           :id id
           :type "judgement"
           :disposition disposition
           :disposition_name disposition_name
           :owner login
           :created now
           :valid_time {:end_time (or (get-in new-judgement [:valid_time :end_time])
                                      time/default-expire-date)
                        :start_time (or (get-in new-judgement [:valid_time :start_time])
                                        now)})))
