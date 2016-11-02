(ns ctia.flows.hooks.after-hooks
  (:require [clojure.string :as str]
            [ctia.domain.entities :as entities]
            [ctia.flows.hook-protocol :refer [Hook]]
            [ctia.lib.async :as la]
            [ctia.schemas.core :refer [StoredJudgement StoredVerdict Verdict]]
            [ctia.store :as store]
            [schema.core :as s]))

(defn- judgement? [{:keys [type]}]
  (= type "judgement"))

(def ^:private judgement-prefix "judgement-")

(s/defn ^:private verdict-id :- s/Str
  [j-id :- s/Str]
  (str "verdict-" (subs j-id
                        (count judgement-prefix))))

(s/defn realize-verdict-wrapper :- StoredVerdict
  [verdict :- Verdict
   {id :id, :as judgement} :- StoredJudgement
   owner :- s/Str]
  (if (str/starts-with? id judgement-prefix)
    (entities/realize-verdict verdict (verdict-id id) owner)
    (entities/realize-verdict verdict owner)))

(defrecord VerdictGenerator []
  Hook
  (init [_] :nothing)
  (destroy [_] :nothing)
  (handle [_ {:keys [observable owner] :as entity} _]
    (when (judgement? entity)
      (when-let [new-verdict (some->
                              (store/read-store :judgement
                                                store/calculate-verdict
                                                observable)
                              (realize-verdict-wrapper entity owner))]
        (la/<!!
         (store/write-store :verdict
                            store/create-verdict
                            (la/on-chan new-verdict)))))))

(s/defn register-hooks :- {s/Keyword [(s/protocol Hook)]}
  [hooks-m :- {s/Keyword [(s/protocol Hook)]}]
  (cond-> hooks-m
    :always (update :after-create #(conj % (->VerdictGenerator)))
    :always (update :after-update #(conj % (->VerdictGenerator)))))
