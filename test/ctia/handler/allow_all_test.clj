(ns ctia.handler.allow-all-test
  (:refer-clojure :exclude [get])
  (:require [ctia.auth :as auth]
            [ctia.handler :as handler]
            [ctia.test-helpers.core :as helpers :refer [post get]]
            [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]))

(use-fixtures :once helpers/fixture-properties)

(use-fixtures :each (join-fixtures [(helpers/fixture-server handler/app)
                                    helpers/fixture-schema-validation
                                    helpers/fixture-allow-all-auth
                                    helpers/fixture-in-memory-store]))

(deftest allow-all-auth-judgement-routes-test
  (testing "POST /ctia/judgement"
    (let [{status :status
           judgement :parsed-body}
          (post "ctia/judgement"
                :body {:observable {:value "1.2.3.4"
                                    :type "ip"}
                       :disposition 2
                       :source "test"
                       :priority 100
                       :severity 100
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:00:00.000-00:00"
                                    :end_time "2016-03-11T00:00:00.000-00:00"}
                       :indicators [{:type "indicator"
                                     :indicator_id "indicator-123"}]})]
      (is (= 200 status))
      (is (deep=
           {:type "judgement"
            :observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :source "test"
            :priority 100
            :severity 100
            :confidence "Low"
            :valid_time {:start_time #inst "2016-02-11T00:00:00.000-00:00"
                         :end_time #inst "2016-03-11T00:00:00.000-00:00"}
            :indicators [{:type "indicator"
                          :indicator_id "indicator-123"}]
            :owner auth/not-logged-in-owner}
           (dissoc judgement :id :created)))

      (testing "GET /ctia/judgement"
        (let [{status :status
               get-judgement :parsed-body}
              (get (str "ctia/judgement/" (:id judgement)))]
          (is (= 200 status))
          (is (deep=
               {:id (:id judgement)
                :type "judgement"
                :observable {:value "1.2.3.4"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :source "test"
                :priority 100
                :severity 100
                :confidence "Low"
                :valid_time {:start_time #inst "2016-02-11T00:00:00.000-00:00"
                             :end_time #inst "2016-03-11T00:00:00.000-00:00"}
                :indicators [{:type "indicator"
                              :indicator_id "indicator-123"}]
                :owner auth/not-logged-in-owner}
               (dissoc get-judgement :created))))))))
