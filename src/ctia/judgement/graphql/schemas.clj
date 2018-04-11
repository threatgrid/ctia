(ns ctia.judgement.graphql.schemas
  (:require [ctia.feedback.graphql.schemas :as feedback]
            [ctia.relationship.graphql.schemas :as relationship]
            [ctia.schemas.graphql
             [flanders :as f]
             [helpers :as g]
             [pagination :as pagination]
             [refs :as refs]
             [sorting :as sorting]]
            [ctia.schemas.sorting :as sort-fields]
            [ctim.schemas.judgement :as ctim-judgement-schema]
            [flanders.utils :as fu]))

(def JudgementType
  (let [{:keys [fields name description]}
        (f/->graphql (fu/optionalize-all ctim-judgement-schema/Judgement)
                     {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship/relatable-entity-fields))))

(def judgement-order-arg
  (sorting/order-by-arg
   "JudgementOrder"
   "judgements"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              sort-fields/judgement-sort-fields))))

(def JudgementConnectionType
  (pagination/new-connection JudgementType))
