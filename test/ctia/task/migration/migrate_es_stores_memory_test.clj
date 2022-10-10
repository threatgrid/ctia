(ns ctia.task.migration.migrate-es-stores-memory-test
  (:require [io.github.frenchy64.fully-satisfies.cleaners :refer [head-hold-detecting-lazy-seq
                                                                  is-live]]
            [ctia.task.migration.migrate-es-stores :as sut]
            [clojure.test :refer [deftest is testing]]))

(deftest do-migrate-query-test
  (doseq [f [#'sut/do-migrate-query-reduce
             #'sut/do-migrate-query-loop]
          buffer-size (range 1 7)]
    (testing (pr-str buffer-size f)
      (let [{:keys [live lseq]} (head-hold-detecting-lazy-seq)
            mult (atom 0)]
        (f buffer-size
           (take (* 4 buffer-size) lseq)
           0
           (fn [_ _]
             (is-live (let [mx (* (swap! mult inc) buffer-size)]
                        (into #{} (range (- mx buffer-size) mx)))
                      live)))
        (is-live #{} live)))))
