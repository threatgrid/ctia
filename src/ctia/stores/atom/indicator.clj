(ns ctia.stores.atom.indicator
  (:require [ctia.schemas.indicator :refer [StoredIndicator]]
            [ctia.schemas.judgement :refer [StoredJudgement]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(def handle-create-indicator (mc/create-handler-from-realized StoredIndicator))
(def handle-read-indicator (mc/read-handler StoredIndicator))
(def handle-update-indicator (mc/update-handler-from-realized StoredIndicator))
(def handle-delete-indicator (mc/delete-handler StoredIndicator))
(def handle-list-indicators (mc/list-handler StoredIndicator))

(s/defn handle-list-indicators-by-judgements :- (s/maybe [StoredIndicator])
  [indicator-state :- (s/atom {s/Str StoredIndicator})
   judgements :- [StoredJudgement]
   params]
  (let [indicator-ids (some->> (map :indicators judgements)
                               (mapcat #(map :indicator_id %))
                               set)]
    (filter (fn [indicator]
              (clojure.set/subset? #{(:id indicator)} indicator-ids))
            (vals @indicator-state))))
