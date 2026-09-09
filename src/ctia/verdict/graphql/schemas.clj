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

;; XFV-20: a verdict is a tenant-local trust decision. Override CTIM's entity
;; description (which states a verdict is chosen from *all* unexpired judgements
;; on the observable) so GraphQL introspection reflects the org-scoping: only
;; judgements owned by the caller's own org contribute, and a caller with no org
;; of its own gets no verdict (null).
;; Customer-visible via introspection, so it describes the behaviour without an
;; internal ticket reference (XFV-20 stays in the source comment above).
(def verdict-description
  (str "A Verdict is chosen from the Judgements owned by the caller's own org on "
       "an Observable which have not yet expired. Verdict calculation is "
       "tenant-local: judgements owned by another org -- even ones shared via "
       "authorized_users / authorized_groups or a public TLP -- do not "
       "contribute. A caller with no org of its own receives no verdict."))

(def VerdictType
  (let [{:keys [fields name]}
        (f/->graphql (fu/optionalize-all ctim-verdict-schema/Verdict)
                     {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object name verdict-description [] (into fields
                                                    verdict-fields))))
