(ns ctia.test-helpers.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [ctia.test-helpers.core :as sut])
  (:import [clojure.lang ExceptionInfo]
           [java.util UUID]))

(deftest split-property-to-keywords-test
  (is (= (sut/split-property-to-keywords
           "a.b.c")
         [:a :b :c])))

(deftest build-transformed-init-config-test
  (assert (not (thread-bound? #'sut/*config-transformers*)))
  (assert (not (thread-bound? #'sut/*properties-overrides*)))
  ;; TODO generate paths and vals from PropertiesSchema
  (testing "defaults to ctia-default.properties values"
    (doseq [:let [test-cases [{:ctia-path [:ctia :auth :type]
                               :ctia-val :allow-all}
                              {:ctia-path [:ctia :encryption :type]
                               :ctia-val :default}]]
            {:keys [ctia-path
                    ctia-val] :as test-case} test-cases]
      (testing test-case
        (is (= (get-in (sut/build-transformed-init-config)
                       ctia-path)
               ctia-val)))))
  (testing "overrides using with-properties"
    (doseq [:let [test-cases [{:ctia-property "ctia.auth.type"
                               :foobar-str "foobar"}]]
            {:keys [ctia-property
                    foobar-str] :as test-case} test-cases
            :let [ctia-path (sut/split-property-to-keywords ctia-property)
                  foobar-kw (keyword foobar-str)]]
      (testing test-case
        (sut/with-properties
          [ctia-property foobar-str]
          (is (= (get-in (sut/build-transformed-init-config) ctia-path)
                 foobar-kw))))))
  (testing "overrides using with-config-transformer*"
    (doseq [:let [test-cases [{:ctia-path [:ctia :auth :type]
                               :foobar-kw :foobar}]]
            {:keys [ctia-path
                    foobar-kw] :as test-case} test-cases]
      (testing test-case
        (sut/with-config-transformer*
          #(assoc-in % ctia-path foobar-kw)
          #(is (= (get-in (sut/build-transformed-init-config) ctia-path)
                  foobar-kw))))))
  (testing "overrides using with-properties and with-config-transformer*"
    (testing "both change config when called with different paths"
      (doseq [:let [test-cases [{:ctia-property1 "ctia.auth.type"
                                 :foobar1-str "foobar1"
                                 :ctia-path2 [:ctia :encryption :type]
                                 :foobar2-kw :foobar2}]]
              {:keys [ctia-property1
                      foobar1-str
                      ctia-path2
                      foobar2-kw] :as test-case} test-cases
              :let [ctia-path1 (sut/split-property-to-keywords ctia-property1)
                    foobar1-kw (keyword foobar1-str)
                    _ (assert (not= ctia-path1 ctia-path2))]]
        (testing test-case
          (sut/with-properties
            [ctia-property1 foobar1-str]
            (sut/with-config-transformer*
              #(assoc-in % ctia-path2 foobar2-kw)
              #(let [config (sut/build-transformed-init-config)]
                 (is (= (get-in config ctia-path1)
                        foobar1-kw))
                 (is (= (get-in config ctia-path2)
                        foobar2-kw))))))))
    (testing "with-config-transformer* wins with conflicting paths"
      (doseq [:let [test-cases [{:ctia-property "ctia.auth.type" 
                                 :foobar1-str "foobar1"
                                 :foobar2-kw :foobar2}]]
              {:keys [ctia-property
                      foobar1-str
                      foobar2-kw] :as test-case} test-cases
              :let [ctia-path (sut/split-property-to-keywords ctia-property)]]
        (testing test-case
          (sut/with-properties
            [ctia-property foobar1-str]
            (sut/with-config-transformer*
              #(assoc-in % ctia-path foobar2-kw)
              #(is (= (get-in (sut/build-transformed-init-config)
                              ctia-path)
                      foobar2-kw))))))))
  (testing "properties are corced to PropertiesSchema"
    (doseq [:let [test-cases [{:ctia-bad-path "obviously.wrong.path"
                               :foobar-str "val"}]]
            {:keys [ctia-bad-path
                    foobar-str] :as test-case} test-cases]
      (testing test-case
        (is (thrown-with-msg?
              ExceptionInfo
              (re-pattern
                (format "Value cannot be coerced to match schema: \\{\"%s\" disallowed-key\\}"
                        ctia-bad-path))
              (sut/with-properties
                [ctia-bad-path foobar-str]
                (sut/build-transformed-init-config)))))))
  (testing "with-properties overrides test properties without also setting System properties"
    (doseq [:let [test-cases [{:ctia-property "ctia.auth.type"
                               :foobar-str "foobar"}]]
            {:keys [ctia-property
                    foobar-str] :as test-case} test-cases
            :let [ctia-path (sut/split-property-to-keywords ctia-property)
                  foobar-kw (keyword foobar-str)]]
      (testing test-case
        (sut/with-properties
          [ctia-property foobar-str]
          (is (= (get-in (sut/build-transformed-init-config)
                         ctia-path)
                 foobar-kw))
          ;; the old behavior of with-properties did the opposite,
          ;; this test is a little crude and is mostly a sanity check.
          ;; more sophisticated checks like
          ;;  "properties are unchanged before/during/after with-properties"
          ;; might be more flaky.
          (is (not= (System/getProperty ctia-property)
                    foobar-str)))))))
