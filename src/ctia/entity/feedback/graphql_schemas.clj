(ns ctia.entity.feedback.graphql-schemas
  (:require
   [ctim.schemas.feedback :as ctim-feedback]
   [ctia.entity.feedback.schemas :as feedbacks]
   [ctia.schemas.graphql
    [flanders :as f]
    [helpers :as g]
    [pagination :as graphql-pagination]
    [resolvers :as resolvers]
    [sorting :as graphql-sorting]]
   [flanders.utils :as fu]))

(def FeedbackType
  (let [{:keys [fields name description]}
        (f/->graphql (fu/optionalize-all ctim-feedback/Feedback))]
    (g/new-object name description [] fields)))

(def feedback-order-arg
  (graphql-sorting/order-by-arg
   "FeedbackOrder"
   "feedbacks"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              feedbacks/feedback-fields))))

(def FeedbackConnectionType
  (graphql-pagination/new-connection FeedbackType))

(def feedback-connection-field
  {:feedbacks
   {:type FeedbackConnectionType
    :description "Related Feedbacks"
    :args (into graphql-pagination/connection-arguments
                feedback-order-arg)
    :resolve
    (fn [context args field-selection src]
      (resolvers/search-feedbacks-by-entity-id
       (:id src)
       context
       args
       field-selection))}})

