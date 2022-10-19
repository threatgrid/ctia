(ns actions.actions-helpers-test
  (:require [clojure.test :refer [deftest is]]
            [actions.actions-helpers :as sut]
            [actions.test-helpers :as th])
  (:import [java.io File]))

(deftest getenv-test
  (is (= (sut/getenv "PWD")
         (System/getenv "PWD")
         (System/getProperty "user.dir"))))

(deftest add-env-test
  (let [github-env-file (File/createTempFile "github-env" nil)
        {:keys [grab-history utils]} (th/mk-utils+getenv-history
                                       {"GITHUB_ENV" (.getPath github-env-file)})
        _ (sut/add-env utils "foo" "bar")
        _ (is (= (grab-history)
                 [{:op :getenv
                   :k "GITHUB_ENV"}]))
        _ (is (= (slurp github-env-file)
                 "foo=bar\n"))]))

(deftest set-json-output-test
  (let [{:keys [grab-history utils]} (th/mk-utils {})
        _ (sut/set-json-output utils "foo" [:a :b :c])
        _ (is (= (grab-history)
                 [{:op :set-output
                   :k "foo"
                   :v "[\"a\",\"b\",\"c\"]"}]))]))

(deftest set-output-test
  (let [github-output-file (File/createTempFile "github-output" nil)
        {:keys [grab-history utils]} (th/mk-utils {"GITHUB_OUTPUT" (.getPath github-output-file)})
        _ (sut/set-output utils "foo" "bar")
        _ (is (empty? (grab-history)))
        _ (is (= "foo=bar\n" (slurp github-output-file)))]))
