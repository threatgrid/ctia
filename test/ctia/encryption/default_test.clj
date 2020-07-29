(ns ctia.encryption.default-test
  (:require
   [ctia.encryption :as enc]
   [ctia.encryption.default :as sut]
   [clojure.test :as t :refer [deftest testing is]]
   [puppetlabs.trapperkeeper.app :as app]
   [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]))

(deftest encryption-default-record-test
  (let [key-path "resources/cert/ctia-encryption.key"
        rec (fn [app]
              (app/get-service app :IEncryption))]
    (testing "failed service init"
      (assert
        (try (with-app-with-config app
               [sut/default-encryption-service]
               {:ctia {:encryption nil}}
               false)
             (catch Throwable e
               (is (= (.getMessage e)
                      "Assert failed: no secret or key filepath provided\n(or secret (:filepath key))"))))))
    (with-app-with-config app
      [sut/default-encryption-service]
      {:ctia {:encryption {:key {:filepath key-path}}}}
      (testing "encrypt and decrypt a string using the record"
        (let [plain "foo"
              enc (enc/encrypt (rec app) "foo")
              dec (enc/decrypt (rec app) enc)]
          (is (string? enc))
          (is (not= plain enc))
          (is (= dec plain)))))))
