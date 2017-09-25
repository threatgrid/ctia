(ns ctia.schemas.graphql.feedback
  (:require [ctia.schemas.graphql
             [flanders :as f]
             [helpers :as g]
             [pagination :as pagination]
             [resolvers :as resolvers]]
            [ctim.schemas.feedback :as ctim-feedback]
            [ctia.schemas.graphql.sorting :as sorting]
            [ctia.schemas.sorting :as sort-fields]))

(def FeedbackType
  (let [{:keys [fields name description]}
        (f/->graphql ctim-feedback/Feedback)]
    (g/new-object name description [] fields)))

(def feedback-order-arg
  (sorting/order-by-arg
   "FeedbackOrder"
   "feedbacks"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              sort-fields/feedback-sort-fields))))

(def FeedbackConnectionType
  (pagination/new-connection FeedbackType))

(def feedback-connection-field
  {:feedbacks
   {:type FeedbackConnectionType
    :description "Related Feedbacks"
    :args (into pagination/connection-arguments
                feedback-order-arg)
    :resolve
    (fn [context args src]
      (resolvers/search-feedbacks-by-entity-id (:id src)
                                               context
                                               args))}})

