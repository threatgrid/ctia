(ns ctia.schemas.feedback
  (:require [ctia.lib.time :as time]
            [ctia.schemas
             [common :as c]
             [relationships :as rel]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defschema Feedback
  "Feedback on any entity.  Is it wrong?  If so why?  Was
  it right-on, and worthy of confirmation?"
  {:id c/ID
   :entity_id c/Reference
   (s/optional-key :source) s/Str
   :feedback (s/enum -1 0 1)
   :reason s/Str
   :tlp c/TLP})

(s/defschema Type
  (s/enum "feedback"))

(s/defschema NewFeedback
  "Schema for submitting new Feedback"
  (st/merge
   Feedback
   (st/optional-keys
    {:id c/ID
     :type Type
     :tlp c/TLP})))

(s/defschema StoredFeedback
  "A feedback record at rest in the storage service"
  (st/merge Feedback
            {:type Type
             :owner s/Str
             :created c/Time}))

(s/defn realize-feedback :- StoredFeedback
  [new-feedback :- NewFeedback
   id :- s/Str
   login :- s/Str]

  (assoc new-feedback
         :id id
         :type "feedback"
         :created (time/now)
         :owner login
         :tlp (:tlp new-feedback c/default-tlp)))
