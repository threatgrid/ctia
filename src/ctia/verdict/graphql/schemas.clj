(ns ctia.verdict.graphql.schemas
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
                            (resolvers/entity-by-id :judgement
                                                    id
                                                    (:ident context)
                                                    field-selection)))}})

;; Override CTIM's entity description so GraphQL introspection reflects the
;; org-scoping (CTIM still says a verdict uses *all* unexpired judgements). The
;; description string is customer-visible, so it carries no ticket reference.
;; See verdict scoping in resources/ctia/public/doc/design.md.
(def verdict-description
  (str "A Verdict is chosen from the Judgements on an Observable which have not "
       "yet expired, that the caller can read and that are owned by the caller's "
       "own org. Verdict calculation is tenant-local: judgements owned by another "
       "org -- even ones shared via authorized_users / authorized_groups or a "
       "public TLP -- do not contribute. A caller with no org of its own receives "
       "no verdict."))

(def VerdictType
  (let [{:keys [fields name]}
        (f/->graphql (fu/optionalize-all ctim-verdict-schema/Verdict)
                     {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object name verdict-description [] (into fields
                                                    verdict-fields))))
