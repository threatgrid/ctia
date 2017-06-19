(ns ctia.schemas.graphql.observable
  (:require [ctia.domain.entities.judgement :as ctim-judgement-entity]
            [ctia.schemas.graphql
             [flanders :as f]
             [helpers :as g]
             [judgement :as judgement]
             [pagination :as pagination]
             [resolvers :as resolvers]
             [verdict :as verdict]]
            [ctia.store :refer :all]
            [ctim.schemas.common :as ctim-common-schema]))

(defn observable-verdict
  [{observable-type :type
    observable-value :value}]
  (some-> (read-store :judgement
                      calculate-verdict
                      {:type observable-type :value observable-value})
          (update :judgement_id ctim-judgement-entity/short-id->long-id)))

(def observable-fields
  {:verdict {:type verdict/VerdictType
             :resolve (fn [_ _ src]
                        (observable-verdict (select-keys src [:type :value])))}
   :judgements {:type judgement/JudgementConnectionType
                :args (into pagination/connection-arguments
                            judgement/judgement-order-arg)
                :resolve (fn [_ args src]
                           (resolvers/search-judgements-by-observable
                            (select-keys src [:type :value])
                            args))}})

(def ObservableType
  (let [{:keys [fields name description]}
        (f/->graphql ctim-common-schema/Observable)]
    (g/new-object name description [] (into observable-fields
                                            fields))))
