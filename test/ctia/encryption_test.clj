(ns ctia.encryption-test
  (:require
   [clojure.test
    :refer
    [deftest is join-fixtures testing use-fixtures]]
   [schema.test :refer [validate-schemas]]
   [ctia.test-helpers
    [core :as test-helpers]
    [es :as es-helpers]]))

(use-fixtures :once (join-fixtures [es-helpers/fixture-properties:es-store
                                    test-helpers/fixture-ctia-fast
                                    validate-schemas]))

(deftest test-encryption-fns
  (testing "encryption shortcuts"
    (let [app (test-helpers/get-current-app)
          {:keys [decrypt encrypt]} (test-helpers/get-service-map app :IEncryption)

          plain "foo"
          enc (encrypt plain)
          dec (decrypt enc)]
      (is (string? enc))
      (is (not= plain enc))
      (is (= dec plain)))))
