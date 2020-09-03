(ns ctia.encryption.default-test
  (:require
   [ctia.encryption.default :as sut]
   [ctia.test-helpers.core :as helpers]
   [clojure.test :as t :refer [deftest testing is]]
   [puppetlabs.trapperkeeper.app :as app]
   [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]))

(deftest encryption-default-record-test
  (let [key-path "resources/cert/ctia-encryption.key"]
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
        (let [{:keys [decrypt encrypt]} (helpers/get-service-map app :IEncryption)
              plain "foo"
              enc (encrypt "foo")
              dec (decrypt enc)]
          (is (string? enc))
          (is (not= plain enc))
          (is (= dec plain)))))))
