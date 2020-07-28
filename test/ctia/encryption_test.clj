(ns ctia.encryption-test
  (:require
   [clojure.test
    :refer
    [deftest is join-fixtures testing use-fixtures]]
   [ctia.encryption :as sut]
   [clj-momo.test-helpers.core :as mth]
   [ctia.test-helpers
    [core :as test-helpers]
    [es :as es-helpers]]
   [puppetlabs.trapperkeeper.app :as app]))

(use-fixtures :once mth/fixture-schema-validation)
(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
                                    test-helpers/fixture-ctia-fast]))

(deftest test-encryption-fns
  (testing "encryption shortcuts"
    (let [app (test-helpers/get-current-app)
          encryption-svc (app/get-service app :IEncryption)
          plain "foo"
          enc (sut/encrypt encryption-svc plain)
          dec (sut/decrypt encryption-svc enc)]
      (is (string? enc))
      (is (not= plain enc))
      (is (= dec plain)))))
