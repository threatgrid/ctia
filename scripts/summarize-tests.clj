#! /usr/bin/env bb

(require '[clojure.pprint :as pp])
(import '[java.io File])

(defn summarize []
  (let [timing-for-prefix (fn [file-prefix]
                            (->> (file-seq (File. "target/test-results"))
                                 (filter (fn [^File f]
                                           (and (.isFile f)
                                                (.startsWith (.getName f) file-prefix))))
                                 (map (comp read-string slurp))
                                 ;; TODO throw on overlapping keys
                                 (apply merge)))
        ns-timing (timing-for-prefix "ns-timing")
        sorted-ns-timing (reverse (sort-by (comp :elapsed-ns val) ns-timing))
        var-timing (timing-for-prefix "var-timing")
        sorted-var-timing (reverse (sort-by (comp :elapsed-ns val) var-timing))]
    (when-some [expected (let [f (File. "dev-resources/ctia_test_timings.edn")]
                           (when (.exists f)
                             (-> f
                                 slurp
                                 read-string)))]
      (println (str "Expected test duration: "
                    (/ (apply + (map :elapsed-ns (vals expected)))
                       1e9)
                    " seconds"))
      (println (str "Actual test duration: "
                    (/ (apply + (map :elapsed-ns (vals ns-timing)))
                       1e9)
                    " seconds")))
    (println "Test namespace summary (slowest to fastest):")
    (pp/pprint sorted-ns-timing)
    (println "Test var summary (slowest to fastest):")
    (pp/pprint sorted-var-timing)
    (-> (File. "target/test-results") .mkdirs)
    (spit "target/test-results/all-test-var-timings.edn" var-timing)
    (spit "target/test-results/all-test-timings.edn" ns-timing)))

(summarize)
