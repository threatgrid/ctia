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
  (:require [actions.actions-helpers :as h]))

(def ^:private java-21-version "21")
(def ^:private java-23-version "23")
(def non-cron-ctia-nsplits
  "Job parallelism for non cron tests."
  10)
(def ^:private cron-ctia-nsplits
  "Job parallelism for cron tests."
  2)

(defn parse-build-config [{:keys [getenv]}]
  (let [m (try (read-string (getenv "CTIA_COMMIT_MESSAGE"))
               (catch Exception _))]
    (-> (when (map? m) m)
        (update :test-suite (fn [test-suite]
                              (or test-suite
                                  (case (getenv "GITHUB_EVENT_NAME")
                                    "schedule" :cron
                                    ("pull_request" "push") :pr)))))))

;; note: if adding new ways to split, ensure actions/upload-artifact names are still unique across run
(defn- valid-split? [{:keys [this_split total_splits
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

(defn- splits-for [base nsplits]
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
    (comp (mapcat #(splits-for
                     {:ci_profiles "default"
                      :java_version %}
                     non-cron-ctia-nsplits))
          (map #(assoc % :test_suite :ci)))
    [java-21-version]))

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
        :java_version java-21-version}]
      (map #(into {:ci_profiles "next-clojure"} %)
           [{:java_version java-21-version}
            {:java_version java-23-version}]))))

(defn edn-matrix [build-config]
  {:post [(seq %)
          (every? valid-split? %)]}
  (case (:test-suite build-config)
    :cron (cron-matrix)
    :pr (non-cron-matrix)))

(defn print-matrix [{:keys [add-env set-json-output] :as utils}]
  (let [build-config (parse-build-config utils)
        _ (println "build-config:" (pr-str build-config))
        ;; inform ./build/run-tests.sh which test suite to run
        _ (add-env utils
                   "CTIA_TEST_SUITE"
                   (case (:test-suite build-config)
                     :cron "cron"
                     :pr "ci"))]
    (set-json-output utils "matrix" (edn-matrix build-config))))

(defn -main [& _args]
  (print-matrix h/utils))

(when (= *file* (System/getProperty "babashka.file")) (-main))
