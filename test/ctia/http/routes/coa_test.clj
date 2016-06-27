(ns ctia.http.routes.coa-test
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-coa-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/coa"
    (let [response (post "ctia/coa"
                         :body {:title "coa"
                                :description "description"
                                :coa_type "Eradication"
                                :objective ["foo" "bar"]
                                :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          coa (:parsed-body response)]
      (is (= 201 (:status response)))
      (is (deep=
           {:type "COA"
            :title "coa"
            :description "description"
            :tlp "green"
            :schema_version schema-version
            :coa_type "Eradication"
            :objective ["foo" "bar"]
            :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"}
            :owner "foouser"}
           (dissoc coa
                   :id
                   :created
                   :modified)))

      (testing "GET /ctia/coa/:id"
        (let [response (get (str "ctia/coa/" (:id coa))
                            :headers {"api_key" "45c1f5e3f05d0"})
              coa (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:type "COA"
                :title "coa"
                :description "description"
                :tlp "green"
                :schema_version schema-version
                :coa_type "Eradication"
                :objective ["foo" "bar"]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :owner "foouser"}
               (dissoc coa
                       :id
                       :created
                       :modified)))))

      (testing "PUT /ctia/coa/:id"
        (let [{updated-coa :parsed-body
               status :status}
              (put (str "ctia/coa/" (:id coa))
                   :body {:title "updated coa"
                          :description "updated description"
                          :tlp "white"
                          :coa_type "Hardening"
                          :objective ["foo" "bar"]
                          :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
                   :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 status))
          (is (deep=
               {:id (:id coa)
                :type "COA"
                :created (:created coa)
                :title "updated coa"
                :description "updated description"
                :tlp "white"
                :schema_version schema-version
                :coa_type "Hardening"
                :objective ["foo" "bar"]
                :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :owner "foouser"}
               (dissoc updated-coa
                       :modified)))))

      (testing "DELETE /ctia/coa/:id"
        (let [response (delete (str "/ctia/coa/" (:id coa))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "/ctia/coa/" (:id coa))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
