(ns reprod-wait-for
  (:require [clojure.test :as t]
            [clojure.java.io :as io]
            [ctia.entity.relationship-test]
            [ctia.shutdown :as shutdown]))

(defn test-var [v]
  {:pre [(var? v)]
   :post [(map? %)]}
  (binding [t/*report-counters* (ref t/*initial-report-counters*)]
    (let [ns-obj (-> v symbol namespace symbol the-ns)]
      (t/do-report {:type :begin-test-ns, :ns ns-obj})
      (t/test-vars [v])
      (t/do-report {:type :end-test-ns, :ns ns-obj})
      (t/do-report (assoc @t/*report-counters* :type :summary))
      @t/*report-counters*)))

(comment
  (test-var #'ctia.entity.relationship-test/test-relationship-routes)
  )

(defn test-var-until-fail [v]
  (loop []
    (let [rs (pmap (fn [v]
                     (let [f (new java.io.StringWriter)]
                       (binding [t/*test-out* f]
                         (let [summary (test-var v)
                               out (str f)]
                           {:summary summary
                            :out out}))))
                   (repeat 1 v))
          failures (remove (comp t/successful? :summary) rs)]
      (if (seq failures)
        (let [{:keys [summary out]} (first failures)]
          (with-open [f (io/writer "out.txt")]
            (binding [*out* f]
              (println out)
              (println summary))))
        (do
          (prn "test-var-until-fail: Try again" v)
          (recur))))))

(defn test-ns-until-fail [nsym]
  (while (t/successful? (t/run-tests nsym))))

(defn -main [& args]
  (with-open [f (io/writer "out.txt")]
    (binding [t/*test-out* f]
      (test-var-until-fail #'ctia.entity.relationship-test/test-relationship-routes)
        #_(test-ns-until-fail)))
  (shutdown/shutdown-ctia-and-agents!))
