#! /usr/bin/env clojure

(ns summarize-tests
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp])
  (:import [java.io File]))

(defn summarize []
  (let [timing (->> (file-seq (io/file "target/test-results"))
                    (filter (fn [^File f]
                              (and (.isFile f)
                                   (.startsWith (.getName f)
                                                "ns-timing"))))
                    (map (comp read-string slurp))
                    (apply merge))]
    (when-some [expected (some-> (io/file "dev-resources/ctia_test_timings.edn")
                                 slurp
                                 read-string)]
      (println (str "Expected test duration: "
                    (/ (apply + (map :elapsed-ns (vals expected)))
                       1e9)
                    " seconds"))
      (println (str "Actual test duration: "
                    (/ (apply + (map :elapsed-ns (vals timing)))
                       1e9)
                    " seconds")))
    (println
      "Test summary:\n"
      (with-out-str
        (pp/pprint
          timing)))))

(summarize)
