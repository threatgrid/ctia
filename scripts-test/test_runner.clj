#!/usr/bin/env bb

;; https://book.babashka.org/#_running_tests
(ns test-runner
  (:require [clojure.test :as t]
            [clojure.set :as set]
            [clojure.java.io :as io]))

;; TODO replace with something automated
(def test-namespaces
  '{"scripts-test/actions/actions_helpers_test.clj" actions.actions-helpers-test
    "scripts-test/actions/print_matrix_test.clj" actions.print-matrix-test
    "scripts-test/actions/setup_env_test.clj" actions.setup-env-test})

(let [fs (-> (into #{}
                   (keep (fn [^File f]
                           (when (.isFile f)
                             (.getPath f))))
                   (file-seq (io/file "scripts-test")))
             (disj "scripts-test/.nrepl-port"))
      expected-extra #{"scripts-test/test_runner.clj"
                       "scripts-test/actions/test_helpers.clj"}
      actual-extra (into #{}
                         (remove test-namespaces)
                         fs)]
  (assert (= expected-extra actual-extra)
          (format "Please add %s to `test-runner/test-namespaces`"
                  (pr-str (sort (map str (set/difference actual-extra expected-extra)))))))

(apply require (vals test-namespaces))                  

(def test-results
  (apply t/run-tests (vals test-namespaces)))           

(def failures-and-errors
  (let [{:keys [fail error]} test-results]
    (+ fail error)))

(System/exit failures-and-errors)                    
