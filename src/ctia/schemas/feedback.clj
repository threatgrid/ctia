(ns ctia.schemas.feedback
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema Feedback
  "Feedback on a Judgement or Verdict.  Is it wrong?  If so why?  Was
  it right-on, and worthy of confirmation?"
  {:id c/ID
   :judgement rel/JudgementReference
   (s/optional-key :source) s/Str
   :feedback (s/enum -1 0 1)
   :reason s/Str})

(s/defschema Type
  (s/enum "feedback"))

(s/defschema NewFeedback
  "Schema for submitting new Feedback"
  (st/merge
   (st/dissoc Feedback
              :id
              :judgement)
   {(s/optional-key :type) Type}))

(s/defschema StoredFeedback
  "A feedback record at rest in the storage service"
  (st/merge Feedback
            {:type Type
             :owner s/Str
             :created c/Time}))

(s/defn realize-feedback :- StoredFeedback
  [new-feedback :- NewFeedback
   id :- s/Str
   login :- s/Str
   judgement-id :- s/Str]
  (assoc new-feedback
         :id id
         :type "feedback"
         :created (time/now)
         :owner login
         :judgement judgement-id))
