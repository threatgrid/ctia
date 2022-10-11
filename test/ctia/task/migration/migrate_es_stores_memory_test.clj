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
            shift (atom -1)
            len (* 4 buffer-size)]
        (f buffer-size
           (take len lseq)
           0
           (fn [_ _]
             (is-live (let [from (swap! shift inc)
                            to (min len (+ from buffer-size
                                           (if (= f #'sut/do-migrate-query-reduce)
                                             1
                                             2)))]
                        (into #{} (range from to)))
                      live)))
        (is-live #{} live)))))
