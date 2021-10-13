(ns actions.print-matrix-test
  (:require [clojure.test :refer [deftest is testing]]
            [actions.actions-helpers :as h]
            [actions.test-helpers :as th]
            [actions.print-matrix :as sut]))

(deftest print-matrix-pull-request+push-test
  (doseq [env-map [{"CTIA_COMMIT_MESSAGE" ""
                    "GITHUB_EVENT_NAME" "pull_request"}
                   {"CTIA_COMMIT_MESSAGE" ""
                    "GITHUB_EVENT_NAME" "push"}
                   {"CTIA_COMMIT_MESSAGE" "{:test-suite :pr}"
                    "GITHUB_EVENT_NAME" "schedule"}]]
    (testing env-map
      (let [{:keys [grab-history state utils]} (th/mk-utils env-map)
            _ (sut/print-matrix utils)
            expected-matrix
            [{:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 0, :total_splits 10, :test_suite :ci}
             {:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 1, :total_splits 10, :test_suite :ci}
             {:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 2, :total_splits 10, :test_suite :ci}
             {:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 3, :total_splits 10, :test_suite :ci}
             {:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 4, :total_splits 10, :test_suite :ci}
             {:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 5, :total_splits 10, :test_suite :ci}
             {:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 6, :total_splits 10, :test_suite :ci}
             {:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 7, :total_splits 10, :test_suite :ci}
             {:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 8, :total_splits 10, :test_suite :ci}
             {:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 9, :total_splits 10, :test_suite :ci}]
            _ (is (= (grab-history)
                     [{:op :add-env, :k "CTIA_TEST_SUITE", :v "ci"}
                      {:op :set-json-output
                       :k "matrix"
                       :v expected-matrix}]))
            ;; convenient to test these here too
            _ (is (= (sut/parse-build-config utils)
                     {:test-suite :pr}))
            _ (is (= (grab-history)
                     []))

            _ (is (= (sut/edn-matrix {:test-suite :pr})
                     (sut/non-cron-matrix)
                     expected-matrix))
            _ (is (= (sut/parse-build-config utils)
                     {:test-suite :pr}))]))))

(deftest print-matrix-cron-test
  (doseq [env-map [{"CTIA_COMMIT_MESSAGE" ""
                    "GITHUB_EVENT_NAME" "schedule"}
                   {"CTIA_COMMIT_MESSAGE" "{:test-suite :cron}"
                    "GITHUB_EVENT_NAME" "push"}
                   {"CTIA_COMMIT_MESSAGE" "{:test-suite :cron}"
                    "GITHUB_EVENT_NAME" "pull_request"}]]
    (testing env-map
      (let [{:keys [grab-history state utils]} (th/mk-utils env-map)
            _ (sut/print-matrix utils)
            expected-matrix [{:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 0, :total_splits 2, :test_suite :cron}
                             {:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 1, :total_splits 2, :test_suite :cron}
                             {:ci_profiles "next-clojure", :java_distribution "adopt", :java_version "11.0.9", :this_split 0, :total_splits 2, :test_suite :cron}
                             {:ci_profiles "next-clojure", :java_distribution "adopt", :java_version "11.0.9", :this_split 1, :total_splits 2, :test_suite :cron}
                             {:ci_profiles "next-clojure", :java_distribution "temurin", :java_version "16", :this_split 0, :total_splits 2, :test_suite :cron}
                             {:ci_profiles "next-clojure", :java_distribution "temurin", :java_version "16", :this_split 1, :total_splits 2, :test_suite :cron}]
            _ (is (= (grab-history)
                     [{:op :add-env, :k "CTIA_TEST_SUITE", :v "cron"}
                      {:op :set-json-output
                       :k "matrix"
                       :v [{:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 0, :total_splits 2, :test_suite :cron}
                           {:ci_profiles "default", :java_distribution "adopt", :java_version "11.0.9", :this_split 1, :total_splits 2, :test_suite :cron}
                           {:ci_profiles "next-clojure", :java_distribution "adopt", :java_version "11.0.9", :this_split 0, :total_splits 2, :test_suite :cron}
                           {:ci_profiles "next-clojure", :java_distribution "adopt", :java_version "11.0.9", :this_split 1, :total_splits 2, :test_suite :cron}
                           {:ci_profiles "next-clojure", :java_distribution "temurin", :java_version "16", :this_split 0, :total_splits 2, :test_suite :cron}
                           {:ci_profiles "next-clojure", :java_distribution "temurin", :java_version "16", :this_split 1, :total_splits 2, :test_suite :cron}]}]))

            ;; convenient to test these here too
            _ (is (= (sut/parse-build-config utils)
                     {:test-suite :cron}))
            _ (is (= (grab-history)
                     []))

            _ (is (= (sut/edn-matrix {:test-suite :cron})
                     (sut/cron-matrix)
                     expected-matrix))
            _ (is (= (sut/parse-build-config utils)
                     {:test-suite :cron}))]))))
