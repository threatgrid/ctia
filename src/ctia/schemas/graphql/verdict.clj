(ns ctia.schemas.graphql.verdict
  (:require [ctia.schemas.graphql
             [flanders :as f]
             [helpers :as g]
             [refs :as refs]
             [resolvers :as resolvers]]
            [ctim.schemas.verdict :as ctim-verdict-schema]))

(def verdict-fields
  {:judgement {:type refs/JudgementRef
               :description "The confidence with which the judgement was made."
               :resolve (fn [context _ src]
                          (when-let [id (:judgement_id src)]
                            (resolvers/judgement-by-id id (:ident context))))}})

(def VerdictType
  (let [{:keys [fields name description]}
        (f/->graphql ctim-verdict-schema/Verdict
                     {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object name description [] (into fields
                                            verdict-fields))))
