(ns ctia.stores.atom.indicator
  (:require [ctia.schemas.indicator
             :refer [NewIndicator StoredIndicator realize-indicator]]
            [ctia.schemas.judgement :refer [StoredJudgement]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(def swap-indicator (mc/make-swap-fn realize-indicator))

(mc/def-create-handler handle-create-indicator
  StoredIndicator NewIndicator swap-indicator (mc/random-id "indicator"))

(mc/def-read-handler handle-read-indicator StoredIndicator)

(mc/def-delete-handler handle-delete-indicator StoredIndicator)

(mc/def-update-handler handle-update-indicator
  StoredIndicator NewIndicator swap-indicator)

(mc/def-list-handler handle-list-indicators StoredIndicator)

(s/defn handle-list-indicators-by-judgements :- (s/maybe [StoredIndicator])
  [indicator-state :- (s/atom {s/Str StoredIndicator})
   judgements :- [StoredJudgement]]
  (let [judgement-ids (set (map :id judgements))]
    ;; Note (polygloton, 2016-03-10):
    ;; Find indicators using the :judgements relationship on the indicators.
    ;; It could be done the other way around, since judgements have :indicators
    ;; relationships.  Not sure which is more correct.
    (filter (fn [indicator]
              (some (fn [judgement-relationship]
                      (let [judgement-id (:judgement_id judgement-relationship)]
                        (contains? judgement-ids judgement-id)))
                    (:judgements indicator)))
            (vals @indicator-state))))
