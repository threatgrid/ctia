(ns ctia.stores.memory.indicator
  (:require [ctia.schemas.indicator
             :refer [NewIndicator StoredIndicator realize-indicator]]
            [ctia.schemas.judgement :refer [StoredJudgement]]
            [ctia.store :refer [IIndicatorStore]]
            [ctia.stores.memory.common :as mc]
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

(defrecord IndicatorStore [state]
  IIndicatorStore
  (create-indicator [_ login new-indicator]
    (handle-create-indicator state login new-indicator))
  (update-indicator [_ id login new-indicator]
    (handle-update-indicator state id login new-indicator))
  (read-indicator [_ id]
    (handle-read-indicator state id))
  (delete-indicator [_ id]
    (handle-delete-indicator state id))
  (list-indicators [_ filter-map]
    (handle-list-indicators state filter-map))
  (list-indicators-by-judgements [_ judgements]
    (handle-list-indicators-by-judgements state judgements)))
