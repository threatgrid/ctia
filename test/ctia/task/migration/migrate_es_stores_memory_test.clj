(ns ctia.task.migration.migrate-es-stores-memory-test
  (:require [io.github.frenchy64.fully-satisfies.cleaners :refer [head-hold-detecting-lazy-seq
                                                                  is-live]]
            [ctia.task.migration.migrate-es-stores :as sut]
            [clojure.test :refer [deftest is testing]]))

(deftest do-migrate-query-test
  (let [{:keys [live lseq]} (head-hold-detecting-lazy-seq)
        mult (atom 0)
        buffer-size 6]
    (sut/do-migrate-query
      buffer-size
      (take (* 4 buffer-size) lseq)
      0
      (fn [_ _]
        (is-live (let [mult (swap! mult inc)]
                   (into #{}
                         (range (min 0 (* (dec mult) buffer-size))
                                (* mult buffer-size))))
                 live)))
    (is-live #{} live)))
