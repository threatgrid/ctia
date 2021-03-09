#!/usr/bin/env bb

(def default-clojure-version "") ;; set in project.clj
(def default-java-version "11.0.9")
(def java-15-version "15")
(def clojure-next "1.10.2-rc1")
(def non-cron-ctia-nsplits
  "Job parallelism for non cron tests."
  10)
(def cron-ctia-nsplits
  "Job parallelism for non tests."
  2)

(defn non-cron-matrix
  "Actions matrix for non cron builds"
  []
  (for [this-split (range non-cron-ctia-nsplits)]
    {:this_split this-split
     :total_splits non-cron-ctia-nsplits
     :clojure_version default-clojure-version
     :java_version default-java-version}))

(defn cron-matrix
  "Actions matrix for cron builds"
  []
  (for [[clojure-version java-version] [[default-clojure-version default-java-version]
                                        [clojure-next default-java-version]
                                        [clojure-next java-15-version]]
        this-split (range cron-ctia-nsplits)]
    {:this_split this-split
     :total_splits cron-ctia-nsplits
     :clojure_version clojure-version
     :java_version java-version}))

(defn edn-matrix []
  {:post [(seq %)]}
  (if (= "schedule" (System/getenv "GITHUB_EVENT_NAME"))
    (cron-matrix)
    (non-cron-matrix)))

(println "::set-output name=matrix::" (json/generate-string (edn-matrix) {:pretty false}))
