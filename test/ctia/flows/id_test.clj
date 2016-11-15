(ns ctia.flows.id-test
  (:require  [clj-momo.test-helpers.core :as mth]
             [clojure.test
              :refer [is deftest join-fixtures testing use-fixtures]]
             [ctia.test-helpers
              [core :as helpers :refer [post]]
              [es :as es-helpers]]))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each (join-fixtures [helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
                                    helpers/fixture-ctia
                                    helpers/fixture-allow-all-auth]))

(deftest test-checked-id
  (testing "Invalid hostname in ID fails"
    (let [{status :status
           {error :error} :parsed-body}
          (post "ctia/judgement"
                :body {:id (str "https://badserver/judgement/judgement-"
                                "67d11c34-a4b1-4e7a-891d-cd4e3de19981")
                       :observable {:type "ip",
                                    :value "10.0.0.1"}
                       :source "source"
                       :priority 99
                       :confidence "High"
                       :severity 88})]
      (is (= 400 status))
      (is (= "Invalid hostname in ID" error)))))
