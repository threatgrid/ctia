#! bb

(require '[clojure.pprint :as pp])
(import '[java.io File])

(defn summarize []
  (let [timing (->> (file-seq (File. "target/test-results"))
                    (filter (fn [^File f]
                              (and (.isFile f)
                                   (.startsWith (.getName f)
                                                "ns-timing"))))
                    (map (comp read-string slurp))
                    (apply merge))]
    (when-some [expected (some-> (File. "dev-resources/ctia_test_timings.edn")
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
    (println "Test summary:")
    (pp/pprint timing)
    (-> (File. "target/test-results")
        .mkdirs)
    (spit "target/test-results/all-test-timings.edn" timing)))

(summarize)
