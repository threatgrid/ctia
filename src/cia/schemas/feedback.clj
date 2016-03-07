(ns cia.schemas.feedback
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [schema.core :as s]
            [schema-tools.core :as st])
  (:import org.joda.time.DateTime))

(s/defschema Feedback
  "Feedback on a Judgement or Verdict.  Is it wrong?  If so why?  Was
  it right-on, and worthy of confirmation?"
  {:id c/ID
   :judgement rel/JudgementReference
   (s/optional-key :source) s/Str
   :feedback (s/enum -1 0 1)
   :reason s/Str})

(s/defschema NewFeedback
  "Schema for submitting new Feedback"
  (st/dissoc Feedback
             :id
             :judgement))

(s/defschema StoredFeedback
  "A feedback record at rest in the storage service"
  (st/merge Feedback
            {:owner s/Str
             :created c/Time}))

(s/defn realize-feedback :- StoredFeedback
  [new-feedback :- NewFeedback
   id :- s/Str
   login :- s/Str
   judgement-id :- s/Str]
  (assoc new-feedback
         :id id
         :created (c/timestamp)
         :owner login
         :judgement judgement-id))
