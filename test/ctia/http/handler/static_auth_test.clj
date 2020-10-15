(ns ctia.http.handler.static-auth-test
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers
             [core :as helpers :refer [POST GET]]
             [es :as es-helpers]]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [ctim.domain.id :as id]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :each
  validate-schemas
  es-helpers/fixture-properties:es-store
  (helpers/fixture-properties:static-auth "kitara" "tearbending")
  helpers/fixture-ctia)

(deftest static-auth-judgement-routes-test
  (testing "POST /ctia/judgement"
    (let [app (helpers/get-current-app)
          {status :status
           judgement :parsed-body}
          (POST app
                "ctia/judgement"
                :body {:observable {:value "1.2.3.4"
                                    :type "ip"}
                       :disposition 2
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :source "test"
                       :priority 100
                       :severity "High"
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:00:00.000-00:00"
                                    :end_time "2016-03-11T00:00:00.000-00:00"}}
                :headers {"Authorization" "tearbending"})
          judgement-id
          (some-> judgement :id id/long-id->id)]
      (is (= 201 status))

      (testing "fails without the correct key"
        (let [{status :status}
              (POST app
                    "ctia/judgement"
                    :body {:observable {:value "1.2.3.4"
                                        :type "ip"}
                           :disposition 2
                           :source "test"
                           :priority 100
                           :severity "High"
                           :confidence "Low"
                           :valid_time {:start_time "2016-02-11T00:00:00.000-00:00"
                                        :end_time "2016-03-11T00:00:00.000-00:00"}})]
          (is (= 401 status)))

        (let [{status :status}
              (POST app
                    "ctia/judgement"
                    :body {:observable {:value "1.2.3.4"
                                        :type "ip"}
                           :disposition 2
                           :source "test"
                           :priority 100
                           :severity "High"
                           :confidence "Low"
                           :valid_time {:start_time "2016-02-11T00:00:00.000-00:00"
                                        :end_time "2016-03-11T00:00:00.000-00:00"}}
                    :headers {"Authorization" "bloodbending"})]
          (is (= 401 status))))

      (testing "GET /ctia/judgement"
        (let [{status :status
               get-judgement :parsed-body}
              (GET app
                   (str "ctia/judgement/" (:short-id judgement-id))
                   :headers {"Authorization" "tearbending"})]
          (is (= 200 status))
          (is (deep=
               {:id (:id judgement)
                :type "judgement"
                :observable {:value "1.2.3.4"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :source "test"
                :timestamp #inst "2042-01-01T00:00:00.000Z"
                :tlp "green"
                :schema_version schema-version
                :priority 100
                :severity "High"
                :confidence "Low"
                :groups ["kitara"]
                :owner "kitara"
                :valid_time {:start_time #inst "2016-02-11T00:00:00.000-00:00"
                             :end_time #inst "2016-03-11T00:00:00.000-00:00"}}
               get-judgement)))
        (testing "fails with any key"
          (let [{status :status}
                (GET app
                     (str "ctia/judgement/" (:short-id judgement-id))
                     :headers {"Authorization" "bloodbending"})]
            (is (= 401 status)))

          (let [{status :status}
                (GET app
                     (str "ctia/judgement/" (:short-id judgement-id)))]
            (is (= 401 status))))))))

