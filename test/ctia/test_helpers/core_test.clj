(ns ctia.test-helpers.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [ctia.test-helpers.core :as sut])
  (:import [clojure.lang ExceptionInfo]
           [java.util UUID]))

(deftest build-transformed-init-config-test
  (assert (empty? @#'sut/*config-transformers*))
  (assert (empty? @#'sut/*properties-overrides*))
  (testing "defaults to ctia-default.properties values"
    (is (= (get-in (sut/build-transformed-init-config)
                   [:ctia :auth :type])
           :allow-all))
    (is (= (get-in (sut/build-transformed-init-config)
                   [:ctia :encryption :type])
           :default)))
  (testing "overrides using with-properties"
    (sut/with-properties
      ["ctia.auth.type" "foobar"]
      (is (= (get-in (sut/build-transformed-init-config)
                     [:ctia :auth :type])
             :foobar))))
  (testing "overrides using with-config-transformer*"
    (sut/with-config-transformer*
      #(assoc-in % [:ctia :auth :type] :foobar)
      #(is (= (get-in (sut/build-transformed-init-config)
                      [:ctia :auth :type])
              :foobar))))
  (testing "overrides using with-properties and with-config-transformer*"
    (testing "both applied with disjoint paths"
      (sut/with-properties
        ["ctia.auth.type" "foobar1"]
        (sut/with-config-transformer*
          #(assoc-in % [:ctia :encryption :type] :foobar2)
          #(let [config (sut/build-transformed-init-config)]
             (is (= (get-in config [:ctia :auth :type])
                    :foobar1))
             (is (= (get-in config [:ctia :encryption :type])
                    :foobar2))))))
    (testing "with-config-transformer* wins with conflicting paths"
      (sut/with-properties
        ["ctia.auth.type" "foobar1"]
        (sut/with-config-transformer*
          #(assoc-in % [:ctia :auth :type] :foobar2)
          #(is (= (get-in (sut/build-transformed-init-config)
                          [:ctia :auth :type])
                  :foobar2))))))
  (testing "properties are corced to PropertiesSchema"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Value cannot be coerced to match schema: \{\"obviously.wrong.path\" disallowed-key\}"
          (sut/with-properties
            ["obviously.wrong.path" "val"]
            (sut/build-transformed-init-config)))))
  (testing "with-properties overrides test properties without changing System properties"
    (let [uuid (UUID/randomUUID)]
      (sut/with-properties
        ["ctia.auth.type" uuid]
        (is (= (get-in (sut/build-transformed-init-config)
                       [:ctia :auth :type])
               uuid))
        (is (not= (System/getProperty "ctia.auth.type")
                  uuid))))))
