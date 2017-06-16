(ns ctia.schemas.graphql.indicator
  (:require [ctia.schemas.graphql
             [feedback :as feedback]
             [flanders :as f]
             [helpers :as g]
             [pagination :as pagination]
             [refs :as refs]
             [relationship :as relationship]]
            [ctim.schemas.indicator :as ctim-indicator-schema]))

(def IndicatorType
  (let [{:keys [fields name description]}
        (f/->graphql
         ctim-indicator-schema/Indicator
         {refs/related-judgement-type-name relationship/RelatedJudgement
          refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object name description []
                  (merge fields
                         feedback/feedback-connection-field
                         relationship/relatable-entity-fields))))

(def IndicatorConnectionType
  (pagination/new-connection IndicatorType))
