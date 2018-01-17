(ns ctia.schemas.graphql.observable
  (:require
   [flanders.utils :as fu]
   [ctia.domain.entities.judgement :as ctim-judgement-entity]
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
    observable-value :value}
   ident]
  (some-> (read-store :judgement
                      calculate-verdict
                      {:type observable-type :value observable-value}
                      ident)
          (update :judgement_id ctim-judgement-entity/short-id->long-id)))

(def observable-fields
  {:verdict {:type verdict/VerdictType
             :resolve (fn [context _ _ src]
                        (observable-verdict (select-keys src [:type :value])
                                            (:ident context)))}
   :judgements {:type judgement/JudgementConnectionType
                :args (into pagination/connection-arguments
                            judgement/judgement-order-arg)
                :resolve (fn [context args field-selection src]
                           (resolvers/search-judgements-by-observable
                            (select-keys src [:type :value])
                            context
                            args
                            field-selection))}})

(def ObservableType
  (let [{:keys [fields name description]}
        (f/->graphql (fu/optionalize-all ctim-common-schema/Observable))]
    (g/new-object name description [] (into observable-fields
                                            fields))))
