(ns ctia.http.routes.observable.judgements-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mht]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [get post]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]))

(use-fixtures :once (join-fixtures [mht/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    helpers/fixture-properties:events-enabled
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-observable-judgements-route
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "test setup: create a judgement (1)"
    (let [{status :status}
          (post "ctia/judgement"
                :body {:observable {:type "ip",
                                    :value "10.0.0.1"}
                       :source "source"
                       :priority 99
                       :confidence "High"
                       :severity "Medium"
                       :external_ids ["judgement-1"]}
                :headers {"API_key" "45c1f5e3f05d0"})]
      (is (= 201 status))))

  (testing "test setup: create a judgement (2)"
    (let [{status :status}
          (post "ctia/judgement"
                :body {:observable {:type "ip",
                                    :value "10.0.0.1"}
                       :source "source"
                       :priority 99
                       :confidence "High"
                       :severity "Medium"
                       :external_ids ["judgement-2"]}
                :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 201 status))))

  (testing "test setup: create a judgement (2)"
    (let [{status :status}
          (post "ctia/judgement"
                :body {:observable {:type "ip",
                                    :value "192.168.1.1"}
                       :source "source"
                       :priority 99
                       :confidence "High"
                       :severity "Medium"
                       :external_ids ["judgement-3"]}
                :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 201 status))))

  (testing "GET /:observable_type/:observable_value/judgements"
    (let [{status :status
           judgements :parsed-body}
          (get "ctia/ip/10.0.0.1/judgements"
               :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 200 status))
      (is (= #{"judgement-1" "judgement-2"}
             (set (mapcat :external_ids judgements)))))))
