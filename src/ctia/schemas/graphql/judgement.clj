(ns ctia.schemas.graphql.judgement
  (:require [ctia.schemas.graphql
             [feedback :as feedback]
             [flanders :as f]
             [helpers :as g]
             [pagination :as pagination]
             [refs :as refs]
             [relationship :as relationship]]
            [ctim.schemas.judgement :as ctim-judgement-schema]))

(def JudgementType
  (let [{:keys [fields name description]}
        (f/->graphql ctim-judgement-schema/Judgement
                     {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object name description []
                  (merge fields
                         feedback/feedback-connection-field
                         relationship/relatable-entity-fields))))

(def JudgementConnectionType
  (pagination/new-connection JudgementType))
