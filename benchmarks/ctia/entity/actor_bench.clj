(ns ctia.entity.actor-bench
  (:require [ctia.test-helpers
             [benchmark :refer [cleanup-ctia!
                                setup-ctia-es-store!]]
             [core :as helpers :refer [POST]]]
            [ctim.examples.actors
             :refer [new-actor-minimal
                     new-actor-maximal]]
            [criterium.core :as bench
             :refer [benchmark quick-benchmark]]))

(def small-actor new-actor-minimal)
(def big-actor (dissoc new-actor-maximal :id))

(defn actor-benchmark-fixture [f]
  (let [{:keys [app] :as m} (setup-ctia-es-store!)
        result (bench/with-progress-reporting
                 (quick-benchmark
                   (f m)
                   {:verbose true}))]
    (cleanup-ctia! app)
    (doto result bench/report-result)))

(defn play [app fixture]
  (POST app
        "ctia/actor"
        :body fixture
        :headers {"Authorization" "45c1f5e3f05d0"}))

(defn big-actor-es-store-benchmark []
  (actor-benchmark-fixture
    (fn [{:keys [app]}]
      (play app big-actor))))

(defn small-actor-es-store-benchmark []
  (actor-benchmark-fixture
    (fn [{:keys [app]}]
      (play app small-actor))))

(defn -main []
  (-> "target/bench/" java.util.File. .mkdirs)
  (spit "target/bench/small-actor-es-store-benchmark.edn"
        (small-actor-es-store-benchmark))
  (spit "target/bench/big-actor-es-store-benchmark.edn"
        (big-actor-es-store-benchmark)))
