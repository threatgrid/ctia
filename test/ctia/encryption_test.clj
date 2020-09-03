(ns ctia.encryption-test
  (:require
   [clojure.test
    :refer
    [deftest is join-fixtures testing use-fixtures]]
   [clj-momo.test-helpers.core :as mth]
   [ctia.test-helpers
    [core :as test-helpers]
    [es :as es-helpers]]))

(use-fixtures :once mth/fixture-schema-validation)
(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
                                    test-helpers/fixture-ctia-fast]))

(deftest test-encryption-fns
  (testing "encryption shortcuts"
    (let [app (test-helpers/get-current-app)
          {:keys [decrypt encrypt]} (helpers/get-service-map app :IEncryption)

          plain "foo"
          enc (encrypt plain)
          dec (decrypt enc)]
      (is (string? enc))
      (is (not= plain enc))
      (is (= dec plain)))))
