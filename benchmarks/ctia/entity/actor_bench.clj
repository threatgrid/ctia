(ns ctia.entity.actor-bench
  (:require [ctia.test-helpers
             [benchmark :refer [fixture-ctia-es-store-with-app]]
             [core :as helpers :refer [POST]]]
            [ctim.examples.actors
             :refer [new-actor-minimal
                     new-actor-maximal]]
            [criterium.core :as bench
             :refer [benchmark quick-benchmark]]))

(def small-actor new-actor-minimal)
(def big-actor (dissoc new-actor-maximal :id))

(defn actor-benchmark-fixture [f]
  (fixture-ctia-es-store-with-app
    (fn [app]
      (let [result (bench/with-progress-reporting
                     (quick-benchmark
                       (f app)
                       {:verbose true}))]
        (doto result bench/report-result)))))

(defn play [app fixture]
  (POST app
        "ctia/actor"
        :body fixture
        :headers {"Authorization" "45c1f5e3f05d0"}))

(def benchmarks
  {:big-actor-es-store-benchmark (fn [app]
                                   (play app big-actor))
   :small-actor-es-store-benchmark (fn [app]
                                     (play app small-actor))})

(defn run-benchmarks []
  (-> "target/bench/" java.io.File. .mkdirs)
  (doseq [[b f] benchmarks]
    (assert (simple-keyword? b))
    (spit (str "target/bench/" (name b) ".edn")
          (-> f
              actor-benchmark-fixture
              (dissoc :results)))))

(defn -main []
  (run-benchmarks)
  (System/exit 0))
