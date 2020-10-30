(ns ctia.dev.test-reporter
  (:require [circleci.test.report :refer [TestReporter]]
            [clojure.test :refer [with-test-out]])
  (:import [java.io File]
           [java.util UUID]))

(deftype TimeReporter [out-dir ns-timing id]
  TestReporter
  (default [this m])

  (pass [this m])

  (fail [this m])

  (error [this m])

  (summary [this m]
    (with-test-out
      (println "Summary: Timing: " @ns-timing)))

  (begin-test-ns [this {:keys [ns]}]
    (let [nsym (ns-name ns)]
      (swap! ns-timing update nsym
             (fn [{:keys [status]}]
               (case status
                 :in-progress (throw (ex-info (str "Overlapping test timing for " nsym)
                                              {}))
                 (:done nil) {:status :in-progress
                              :start-ns (System/nanoTime)})))))

  (end-test-ns [this {:keys [ns]}]
    (let [nsym (ns-name ns)]
      (swap! ns-timing update nsym
             (fn [{:keys [status start-ns] :as timing}]
               (case status
                 :in-progress {:status :done
                               :elapsed-ns (- (System/nanoTime) start-ns)}
                 (:done nil) (throw (ex-info (str "end-test-ns called without begin-test-ns: " nsym)
                                             (or timing {})))))))
    (-> out-dir File. .mkdirs)
    (spit (str out-dir "/ns-timing" #_id ".edn")
          @ns-timing
          :append true)
    (with-test-out
      (println "Timing: " @ns-timing)))

  (begin-test-var [this m])

  (end-test-var [this {:keys [elapsed] var-ref :var}]
    (with-test-out
      (println (format "%5f%s for %s."
                       elapsed
                       "s"
                       (symbol var-ref))))))

(defn time-reporter [config]
  (->TimeReporter (:test-results-dir config)
                  (atom {})
                  (str (UUID/randomUUID))))
