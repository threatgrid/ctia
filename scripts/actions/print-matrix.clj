#!/usr/bin/env bb

(def default-java-version "11.0.9")
(def java-15-version "15")
(def non-cron-ctia-nsplits
  "Job parallelism for non cron tests."
  10)
(def cron-ctia-nsplits
  "Job parallelism for cron tests."
  2)

(defn non-cron-matrix
  "Actions matrix for non cron builds"
  []
  (for [this-split (range non-cron-ctia-nsplits)]
    {:this_split this-split
     :total_splits non-cron-ctia-nsplits
     :ci_profiles "default"
     :java_version default-java-version}))

(defn cron-matrix
  "Actions matrix for cron builds"
  []
  (for [base [{:ci_profiles "default"
               :java_version default-java-version}
              {:ci_profiles "next-clojure"
               :java_version default-java-version}
              {:ci_profiles "next-clojure"
               :java_version java-15-version}]
        this-split (range cron-ctia-nsplits)]
    (assoc base
           :this_split this-split
           :total_splits cron-ctia-nsplits)))

(defn edn-matrix []
  {:post [(seq %)]}
  (if (= "schedule" (System/getenv "GITHUB_EVENT_NAME"))
    (cron-matrix)
    (non-cron-matrix)))

(println "::set-output name=matrix::" (json/generate-string (edn-matrix) {:pretty false}))
