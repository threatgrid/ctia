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
  (let [indicator-ids (some->> (map :indicators judgements)
                               (mapcat #(map :indicator_id %))
                               set)]
    (filter (fn [indicator]
              (clojure.set/subset? #{(:id indicator)} indicator-ids))
            (vals @indicator-state))))
