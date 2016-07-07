(ns ctia.http.handler.allow-all-test
  (:refer-clojure :exclude [get])
  (:require [ctia.auth :as auth]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.http.handler :as handler]
            [ctia.test-helpers.core :as helpers :refer [post get]]
            [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
            [ctim.schemas.common :as c]))

(use-fixtures :once helpers/fixture-schema-validation)

(use-fixtures :each (join-fixtures [helpers/fixture-properties:clean
                                    helpers/fixture-properties:atom-store
                                    helpers/fixture-ctia
                                    helpers/fixture-allow-all-auth]))

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
                       :indicators [{:indicator_id "indicator-123"}]})]
      (is (= 201 status))
      (is (deep=
           {:type "judgement"
            :observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :source "test"
            :tlp "green"
            :schema_version schema-version
            :priority 100
            :severity 100
            :confidence "Low"
            :valid_time {:start_time #inst "2016-02-11T00:00:00.000-00:00"
                         :end_time #inst "2016-03-11T00:00:00.000-00:00"}
            :indicators [{:indicator_id "indicator-123"}]
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
                :tlp "green"
                :schema_version schema-version
                :priority 100
                :severity 100
                :confidence "Low"
                :valid_time {:start_time #inst "2016-02-11T00:00:00.000-00:00"
                             :end_time #inst "2016-03-11T00:00:00.000-00:00"}
                :indicators [{:indicator_id "indicator-123"}]
                :owner auth/not-logged-in-owner}
               (dissoc get-judgement :created))))))))
