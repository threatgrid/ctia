(ns ctia.encryption.default-test
  (:require
   [ctia.encryption :as enc]
   [ctia.encryption.default :as sut]
   [ctia.encryption.default-core :as sut-core]
   [clojure.test :as t :refer [deftest testing is]]
   [lock-key.core :refer [decrypt-from-base64
                          encrypt-as-base64]])
  (:import [clojure.lang ExceptionInfo]))

;; TODO update to use with-app-with-config when tk is no longer global
(deftest simulated-default-encryption-service-test
  (let [key-path "resources/cert/ctia-encryption.key"]
    (testing "encryption-key requires key"
      (assert (try (sut-core/init {})
                   false
                   (catch ExceptionInfo e
                     (is (= (.getMessage e)
                            "no secret or key filepath provided"))
                     true))
              "encryption-key throws exception without secret"))
    (testing "encrypt and decrypt a string using encryption-key's secret"
      (let [context (-> {:key {:filepath key-path}}
                        sut-core/init
                        sut-core/start)
            plain "foo"
            enc (sut-core/encrypt context plain)
            dec (sut-core/decrypt context enc)]
        (is (string? enc))
        (is (not= plain enc))
        (is (= dec plain))))))
