(ns ctia.http.handler.static-auth-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers
             [core :as helpers :refer [post get]]
             [es :as es-helpers]]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [ctim.domain.id :as id]))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each
  helpers/fixture-properties:clean
  es-helpers/fixture-properties:es-store
  (helpers/fixture-properties:static-auth "kitara" "tearbending")
  helpers/fixture-ctia)

(deftest static-auth-judgement-routes-test
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
                                    :end_time "2016-03-11T00:00:00.000-00:00"}}
                :headers {"api_key" "tearbending"})
          judgement-id
          (some-> judgement :id id/long-id->id)]
      (is (= 201 status))

      (testing "GET /ctia/judgement"
        (let [{status :status
               get-judgement :parsed-body}
              (get (str "ctia/judgement/" (:short-id judgement-id))
                   :headers {"api_key" "tearbending"})]
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
                             :end_time #inst "2016-03-11T00:00:00.000-00:00"}}
               get-judgement)))

        (testing "fails without the correct key"
          (let [{status :status}
                (get (str "ctia/judgement/" (:short-id judgement-id))
                     :headers {"api_key" "bloodbending"})]
            (is (= 403 status)))

          (let [{status :status}
                (get (str "ctia/judgement/" (:short-id judgement-id)))]
            (is (= 403 status))))))))
