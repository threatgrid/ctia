(ns ctia.entity.feedback.schemas
  (:require [clj-momo.lib.time :as time]
            [ctia.domain
             [access-control :refer [properties-default-tlp]]
             [entities :refer [schema-version]]]
            [ctia.schemas
             [utils :as csu]
             [core :refer [def-acl-schema def-stored-schema TempIDs]]
             [sorting :as sorting]]
            [ctim.schemas.feedback :as feedbacks]
            [flanders.utils :as fu]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def-acl-schema Feedback
  feedbacks/Feedback
  "feedback")

(def-acl-schema PartialFeedback
  (fu/optionalize-all feedbacks/Feedback)
  "partial-feedback")

(s/defschema PartialFeedbackList
  [PartialFeedback])

(def-acl-schema NewFeedback
  feedbacks/NewFeedback
  "new-feedback")

(def-stored-schema StoredFeedback Feedback)

(s/defschema PartialStoredFeedback
  (csu/optional-keys-schema StoredFeedback))

(s/defn realize-feedback :- StoredFeedback
  [new-feedback :- NewFeedback
   id :- s/Str
   tempids :- (s/maybe TempIDs)
   owner :- s/Str
   groups :- [s/Str]]
  (assoc new-feedback
         :id id
         :type "feedback"
         :created (time/now)
         :owner owner
         :groups groups
         :tlp (:tlp new-feedback (properties-default-tlp))
         :schema_version schema-version))

(def feedback-fields
  (concat sorting/base-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:entity_id
           :feedback
           :reason]))
