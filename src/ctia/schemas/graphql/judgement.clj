(ns ctia.schemas.graphql.judgement
  (:require [ctia.schemas.graphql
             [feedback :as feedback]
             [flanders :as f]
             [helpers :as g]
             [pagination :as pagination]
             [refs :as refs]
             [relationship :as relationship]
             [sorting :as sorting]]
            [ctia.schemas.sorting :as sort-fields]
            [ctim.schemas.judgement :as ctim-judgement-schema]))

(def JudgementType
  (let [{:keys [fields name description]}
        (f/->graphql ctim-judgement-schema/Judgement
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
