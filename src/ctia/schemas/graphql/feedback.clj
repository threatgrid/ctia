(ns ctia.schemas.graphql.feedback
  (:require [ctia.schemas.graphql
             [flanders :as f]
             [helpers :as g]
             [pagination :as pagination]
             [resolvers :as resolvers]]
            [ctim.schemas.feedback :as ctim-feedback]))

(def FeedbackType
  (let [{:keys [fields name description]}
        (f/->graphql ctim-feedback/Feedback)]
    (g/new-object name description [] fields)))

(def FeedbackConnectionType
  (pagination/new-connection FeedbackType))

(def feedback-connection-field
  {:feedbacks
   {:type FeedbackConnectionType
    :description "Related Feedbacks"
    :args pagination/connection-arguments
    :resolve (fn [_ args src]
               (resolvers/search-feedbacks-by-entity-id (:id src) args))}})

