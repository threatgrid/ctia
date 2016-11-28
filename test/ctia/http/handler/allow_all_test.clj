(ns ctia.http.handler.allow-all-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.auth :as auth]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.http.handler :as handler]
            [ctia.test-helpers
             [core :as helpers :refer [post get]]
             [es :as es-helpers]]
            [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each (join-fixtures [helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
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
                       :severity "High"
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:00:00.000-00:00"
                                    :end_time "2016-03-11T00:00:00.000-00:00"}})

          judgement-id
          (id/long-id->id (:id judgement))]
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
            :severity "High"
            :confidence "Low"
            :valid_time {:start_time #inst "2016-02-11T00:00:00.000-00:00"
                         :end_time #inst "2016-03-11T00:00:00.000-00:00"}
            :owner auth/not-logged-in-owner}
           (dissoc judgement :id :created)))

      (testing "GET /ctia/judgement"
        (let [{status :status
               get-judgement :parsed-body}
              (get (str "ctia/judgement/" (:short-id judgement-id)))]
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
                :severity "High"
                :confidence "Low"
                :valid_time {:start_time #inst "2016-02-11T00:00:00.000-00:00"
                             :end_time #inst "2016-03-11T00:00:00.000-00:00"}
                :owner auth/not-logged-in-owner}
               (dissoc get-judgement :created))))))))
