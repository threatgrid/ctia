(ns ctia.schemas.graphql.verdict
  (:require
   [flanders.utils :as fu]
   [ctia.schemas.graphql
    [flanders :as f]
    [helpers :as g]
    [refs :as refs]
    [resolvers :as resolvers]]
   [ctim.schemas.verdict :as ctim-verdict-schema]))

(def verdict-fields
  {:judgement {:type refs/JudgementRef
               :description "The confidence with which the judgement was made."
               :resolve (fn [context _ field-selection src]
                          (when-let [id (:judgement_id src)]
                            (resolvers/judgement-by-id id (:ident context) field-selection)))}})

(def VerdictType
  (let [{:keys [fields name description]}
        (f/->graphql (fu/optionalize-all ctim-verdict-schema/Verdict)
                     {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object name description [] (into fields
                                            verdict-fields))))
