(ns ctia.schemas.graphql.indicator
  (:require [ctia.schemas.graphql
             [feedback :as feedback]
             [flanders :as f]
             [helpers :as g]
             [pagination :as pagination]
             [refs :as refs]
             [relationship :as relationship]
             [sorting :as sorting]]
            [ctia.schemas.sorting :as sort-fields]
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

(def indicator-order-arg
  (sorting/order-by-arg
   "IndicatorOrder"
   "indicators"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              sort-fields/indicator-sort-fields))))

(def IndicatorConnectionType
  (pagination/new-connection IndicatorType))
