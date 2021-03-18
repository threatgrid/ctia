#!/usr/bin/env bb

(def default-java-version "11.0.9")
(def java-15-version "15")
(def non-cron-ctia-nsplits
  "Job parallelism for non cron tests."
  10)
(def cron-ctia-nsplits
  "Job parallelism for cron tests."
  2)

(defn splits-for [base nsplits]
  {:post [(= (range nsplits)
             (map :this_split %))
          (= #{nsplits}
             (into #{} (map :total_splits %)))]}
  (for [this-split (range nsplits)]
    (assoc base
           :this_split this-split
           :total_splits nsplits)))

(defn non-cron-matrix
  "Actions matrix for non cron builds"
  []
  (splits-for
    {:ci_profiles "default"
     :java_version default-java-version}
    non-cron-ctia-nsplits))

(defn cron-matrix
  "Actions matrix for cron builds"
  []
  (mapcat #(splits-for % cron-ctia-nsplits)
          (concat
            [{:ci_profiles "default"
              :java_version default-java-version}]
            (map #(into {:ci_profiles "next-clojure"} %)
                 [{:java_version default-java-version}
                  {:java_version java-15-version}]))))

(defn edn-matrix []
  {:post [(seq %)]}
  (if (= "schedule" (System/getenv "GITHUB_EVENT_NAME"))
    (cron-matrix)
    (non-cron-matrix)))

(println "::set-output name=matrix::" (json/generate-string (edn-matrix) {:pretty false}))
