(ns ctia.domain.entities.verdict
  (:require
    [clj-momo.lib.time :as t]
    [ctia.domain.entities.judgement :as judgement]))

(defn with-long-id [entity]
  (update entity :judgement_id judgement/short-id->long-id))

(defn expired?
  ([verdict]
   (expired? verdict (t/now)))
  ([verdict date]
   (if-let [end-time (get-in verdict [:valid_time :end_time])]
     (t/after? date end-time)
     false)))
