(ns ctia.stores.atom.indicator
  (:require [ctia.domain.id :as id]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.properties :refer [properties]]
            [ctim.schemas
             [indicator :refer [StoredIndicator]]
             [judgement :refer [StoredJudgement]]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(defn long-id->short-id [long-id]
  (id/short-id (id/long-id->id long-id)))

(def handle-create-indicator (mc/create-handler-from-realized StoredIndicator))
(def handle-read-indicator (mc/read-handler StoredIndicator))
(def handle-update-indicator (mc/update-handler-from-realized StoredIndicator))
(def handle-delete-indicator (mc/delete-handler StoredIndicator))
(def handle-list-indicators (mc/list-handler StoredIndicator))

(s/defn handle-list-indicators-by-judgements :- (list-response-schema StoredIndicator)
  [indicator-state :- (s/atom {s/Str StoredIndicator})
   judgements :- [StoredJudgement]
   params]
  (let [indicator-ids (some->> (map :indicators judgements)
                               (mapcat #(map :indicator_id %))
                               (map #(long-id->short-id %))
                               set)]
    (handle-list-indicators indicator-state {:id indicator-ids} params)))
