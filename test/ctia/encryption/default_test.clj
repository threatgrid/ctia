(ns ctia.encryption.default-test
  (:require
   [ctia.encryption :as enc]
   [ctia.encryption.default :as sut]
   [clojure.test :as t :refer [deftest testing is]]))

(deftest encryption-default-record-test
  (let [key-path "resources/cert/ctia-encryption.key"
        rec (sut/map->EncryptionService
             {:state (atom nil)})]
    (testing "service init"
      (try (enc/init rec {})
           (catch Throwable e
             (is (= (.getMessage e)
                    "Assert failed: no secret or key filepath provided\n(or secret (:filepath key))"))))
      (enc/init rec {:key {:filepath key-path}})
      (testing "encrypt and decrypt a string using the record"
        (let [plain "foo"
              enc (enc/encrypt rec "foo")
              dec (enc/decrypt rec enc)]
          (is (string? enc))
          (is (not= plain enc))
          (is (= dec plain)))))))
