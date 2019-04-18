(ns ctia.entity.feedback.schemas
  (:require [clj-momo.lib.time :as time]
            [ctia.domain
             [access-control :refer [properties-default-tlp]]
             [entities :refer [default-realize-fn]]]
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

(def realize-feedback
  (default-realize-fn "feedback" NewFeedback StoredFeedback))

(def feedback-fields
  (concat sorting/base-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:entity_id
           :feedback
           :reason]))
