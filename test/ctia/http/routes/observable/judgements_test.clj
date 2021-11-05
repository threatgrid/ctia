(ns ctia.http.routes.observable.judgements-test
  (:require [clj-momo.test-helpers.core :as mht]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [GET POST]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]
            [ctim.domain.id :as id]))

(use-fixtures :once (join-fixtures [mht/fixture-schema-validation
                                    helpers/fixture-properties:events-enabled
                                    whoami-helpers/fixture-server]))

(deftest test-observable-judgements-route
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "test setup: create a judgement (1)"
       (let [{status :status}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:type "ip",
                                       :value "10.0.0.1"}
                          :source "source"
                          :priority 99
                          :confidence "High"
                          :severity "Medium"
                          :external_ids ["judgement-1"]
                          :disposition_name "Malicious"
                          :disposition 2
                          :valid_time {:start_time "2017-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))))

     (testing "test setup: create a judgement (2)"
       (let [{status :status}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:type "ip",
                                       :value "10.0.0.1"}
                          :source "source"
                          :priority 99
                          :confidence "High"
                          :severity "Medium"
                          :disposition 5
                          :external_ids ["judgement-2"]
                          :valid_time {:start_time "2017-02-11T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))))

     (testing "test setup: create a judgement (3)"
       (let [{status :status}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:type "ip",
                                       :value "10.0.0.1"}
                          :source "source"
                          :priority 99
                          :confidence "High"
                          :severity "Medium"
                          :disposition 5
                          :external_ids ["judgement-3"]
                          :valid_time {:start_time "2017-02-12T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))))

     (testing "test setup: create a judgement (4)"
       (let [{status :status}
             (POST app
                   "ctia/judgement"
                   :body {:observable {:type "ip",
                                       :value "192.168.1.1"}
                          :source "source"
                          :priority 99
                          :confidence "High"
                          :severity "Medium"
                          :disposition 5
                          :external_ids ["judgement-4"]
                          :valid_time {:start_time "2017-02-13T00:40:48.212-00:00"}}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))))

     (testing "GET /:observable_type/:observable_value/judgements"
       (let [{status :status
              judgements :parsed-body}
             (GET app
                  "ctia/ip/10.0.0.1/judgements"
                  :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 200 status))
         (is (= #{"judgement-1" "judgement-2" "judgement-3"}
                (set (mapcat :external_ids judgements))))))

     (testing "GET /:observable_type/:observable_value/judgements?sort_by=disposition%3Aasc%2Cvalid_time.start_time%3Adesc"
       (let [{status :status
              judgements :parsed-body}
             (GET app
                  "ctia/ip/10.0.0.1/judgements?sort_by=disposition%3Aasc%2Cvalid_time.start_time%3Adesc"
                  :params {:sort_by "disposition:asc,valid_time.start_time:desc"}
                  :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 200 status))
         (is (= ["judgement-1" "judgement-3" "judgement-2"]
                (mapcat :external_ids judgements))))))))
