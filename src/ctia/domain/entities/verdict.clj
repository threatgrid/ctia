(ns ctia.domain.entities.verdict
  (:require
    [ctia.domain.entities.judgement :as judgement]))

(defn with-long-id [entity]
  (update entity :judgement_id judgement/short-id->long-id))
