(ns ctia.dev.test-reporter
  (:require
   [circleci.test.report]))

(deftype TimeReporter [ns-timing]
  circleci.test.report/TestReporter
  (circleci.test.report/default [this m])

  (pass [this m])

  (fail [this m])

  (error [this m])

  (summary [this m]
    (print "Timing: "
           @ns-timing))

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
                                             (or timing {}))))))))

  (begin-test-var [this m])

  (end-test-var [this {:keys [elapsed] var-ref :var}]
    (println (format "%10.2f ms for %s."
                     (* elapsed 1000.0)
                     (symbol var-ref)))))

(defn time-reporter [_config]
  (->TimeReporter (atom {})))
