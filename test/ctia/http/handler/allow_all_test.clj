(ns ctia.http.handler.allow-all-test
  (:require [ctia.domain.entities :refer [schema-version]]
            [ctia.test-helpers
             [core :as helpers :refer [POST GET]]
             [es :as es-helpers]]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [ctim.domain.id :as id]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :each
  validate-schemas
  es-helpers/fixture-properties:es-store
  helpers/fixture-allow-all-auth
  helpers/fixture-ctia)

(deftest allow-all-auth-judgement-routes-test
  (testing "POST /ctia/judgement"
    (let [app (helpers/get-current-app)
          {status :status
           judgement :parsed-body}
          (POST app
                "ctia/judgement"
                :body {:observable {:value "1.2.3.4"
                                    :type "ip"}
                       :disposition 2
                       :source "test"
                       :priority 100
                       :timestamp #inst "2042-01-01T00:00:00.000Z"
                       :severity "High"
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:00:00.000-00:00"
                                    :end_time "2016-03-11T00:00:00.000-00:00"}})

          judgement-id
          (id/long-id->id (:id judgement))]
      (is (= 201 status))
      (is (=
           {:type "judgement"
            :observable {:value "1.2.3.4"
                         :type "ip"}
            :disposition 2
            :disposition_name "Malicious"
            :source "test"
            :tlp "green"
            :schema_version schema-version
            :priority 100
            :timestamp #inst "2042-01-01T00:00:00.000Z"
            :severity "High"
            :confidence "Low"
            :groups ["Administrators"]
            :owner "Unknown"
            :valid_time {:start_time #inst "2016-02-11T00:00:00.000-00:00"
                         :end_time #inst "2016-03-11T00:00:00.000-00:00"}}
           (dissoc judgement :id :created :modified)))

      (testing "GET /ctia/judgement"
        (let [{status :status
               get-judgement :parsed-body}
              (GET app
                   (str "ctia/judgement/" (:short-id judgement-id)))]
          (is (= 200 status))
          (is (=
               {:id (:id judgement)
                :created (:created judgement)
                :modified (:modified judgement)
                :type "judgement"
                :observable {:value "1.2.3.4"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :source "test"
                :tlp "green"
                :schema_version schema-version
                :priority 100
                :timestamp #inst "2042-01-01T00:00:00.000Z"
                :severity "High"
                :confidence "Low"
                :groups ["Administrators"]
                :owner "Unknown"
                :valid_time {:start_time #inst "2016-02-11T00:00:00.000-00:00"
                             :end_time #inst "2016-03-11T00:00:00.000-00:00"}}
               get-judgement)))))))
