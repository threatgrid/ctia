(ns ctia.bulk.core-test
  (:require [ctia.auth.allow-all :refer [identity-singleton]]
            [ctia.bulk.core :as sut :refer [read-fn]]
            [ctia.test-helpers.core :as helpers]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest testing is use-fixtures]]))

(use-fixtures :once mth/fixture-schema-validation)

(deftest read-entities-test
  (testing "Attempting to read an unreachable entity should not throw"
    (let [get-in-config (helpers/build-get-in-config-fn)
          res (sut/read-entities ["judgement-123"]
                                 :judgement
                                 identity-singleton
                                 {:ConfigService {:get-in-config get-in-config}
                                  :StoreService {:read-store (constantly nil)}})]
      (is (= [nil] res)))))
