#!/usr/bin/env bb

;; determines the build matrix for the GitHub Actions build. 
;; try it locally:
;;   # normal builds
;;   $ GITHUB_ENV=$(mktemp) GITHUB_EVENT_NAME=pull_request ./scripts/actions/print-matrix.clj
;;   $ GITHUB_ENV=$(mktemp) GITHUB_EVENT_NAME=push ./scripts/actions/print-matrix.clj
;;   # cron build
;;   $ GITHUB_ENV=$(mktemp) GITHUB_EVENT_NAME=schedule ./scripts/actions/print-matrix.clj
;;   $ GITHUB_ENV=$(mktemp) CTIA_COMMIT_MESSAGE='{:test-suite :cron} try cron build' GITHUB_EVENT_NAME=push ./scripts/actions/print-matrix.clj

(ns actions.print-matrix
  (:require [actions.actions-helpers :refer [add-env]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def default-java-version "11.0.9")
(def java-15-version "15")
(def non-cron-ctia-nsplits
  "Job parallelism for non cron tests."
  10)
(def cron-ctia-nsplits
  "Job parallelism for cron tests."
  2)

(defn parse-build-config []
  (let [m (try (read-string (System/getenv "CTIA_COMMIT_MESSAGE"))
               (catch Exception _))] 
    (-> (when (map? m) m)
        (update :test-suite (fn [test-suite]
                              (or test-suite
                                  (case (System/getenv "GITHUB_EVENT_NAME")
                                    "schedule" :cron
                                    ("pull_request" "push") :pr)))))))

(def build-config (parse-build-config))

(println "build-config:" (pr-str build-config))

(defn valid-split? [{:keys [this_split total_splits
                            java_version ci_profiles] :as m}]
  (and (= #{:this_split :total_splits
            :java_version :ci_profiles
            :test_suite} (set (keys m)))
       (#{:ci :cron} (:test_suite m))
       (nat-int? this_split)
       ((every-pred nat-int? pos?) total_splits)
       (<= 0 this_split)
       (< this_split total_splits)
       ((every-pred string? seq) java_version)
       ((every-pred string? seq) ci_profiles)))

(defn splits-for [base nsplits]
  {:pre [(pos? nsplits)]
   :post [(= (range nsplits)
             (map :this_split %))
          (= #{nsplits}
             (into #{} (map :total_splits) %))]}
  (for [this-split (range nsplits)]
    (assoc base
           :this_split this-split
           :total_splits nsplits)))

(defn non-cron-matrix
  "Actions matrix for non cron builds"
  []
  {:post [(every? valid-split? %)
          (zero? (mod (count %) non-cron-ctia-nsplits))]}
  (sequence
    (map #(assoc % :test_suite :ci))
    (splits-for
      {:ci_profiles "default"
       :java_version default-java-version}
      non-cron-ctia-nsplits)))

(defn cron-matrix
  "Actions matrix for cron builds"
  []
  {:post [(every? valid-split? %)
          (zero? (mod (count %) cron-ctia-nsplits))]}
  (sequence
    (comp (mapcat #(splits-for % cron-ctia-nsplits))
          (map #(assoc % :test_suite :cron)))
    (concat
      [{:ci_profiles "default"
        :java_version default-java-version}]
      (map #(into {:ci_profiles "next-clojure"} %)
           [{:java_version default-java-version}
            {:java_version java-15-version}]))))

(defn edn-matrix []
  {:post [(seq %)]}
  (case (:test-suite build-config)
    :cron (cron-matrix)
    :pr (non-cron-matrix)))

;; inform ./build/run-tests.sh which test suite to run
(add-env "CTIA_TEST_SUITE"
         (case (:test-suite build-config)
           :cron "cron"
           :pr "ci"))

(let [jstr (json/generate-string (edn-matrix) {:pretty false})]
  ;; Actions does not print ::set-ouput commands to the build output
  (println (str "DEBUG: " jstr))
  (println (str "::set-output name=matrix::" jstr)))
