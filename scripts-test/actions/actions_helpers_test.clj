(ns actions.actions-helpers-test
  (:require [clojure.test :refer [deftest is testing]]
            [actions.actions-helpers :as sut]
            [actions.test-helpers :as th]
            [cheshire.core :as json])
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
  (is (.contains
        (with-out-str
          (sut/set-output "foo" "bar"))
        "::set-output name=foo::bar\n")))
