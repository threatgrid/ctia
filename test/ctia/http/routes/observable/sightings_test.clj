(ns ctia.http.routes.observable.sightings-test
  (:require [schema.test :refer [validate-schemas]]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [GET POST]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [test-for-each-store-with-app]]]))

(use-fixtures :once (join-fixtures [validate-schemas
                                    helpers/fixture-properties:events-enabled
                                    whoami-helpers/fixture-server]))

(deftest test-sightings-route
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (testing "test setup: create a sighting (1)"
       (let [{status :status}
             (POST app
                   "ctia/sighting"
                   :body {:observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                          :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                          :observables [{:type "ip",
                                         :value "10.0.0.1"}
                                        {:type "user"
                                         :value "foobar"}]
                          :external_ids ["sighting-1"]}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))))


     (testing "test setup: create a sighting (2)"
       (let [{status :status}
             (POST app
                   "ctia/sighting"
                   :body {:observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                          :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                          :observables [{:type "user"
                                         :value "foobar"}]
                          :external_ids ["sighting-2"]}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))))

     (testing "test setup: create a sighting (3)"
       (let [{status :status}
             (POST app
                   "ctia/sighting"
                   :body {:observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                          :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                          :observables [{:type "ip",
                                         :value "10.0.0.1"}]
                          :external_ids ["sighting-3"]}
                   :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 201 status))))

     (testing "GET /:observable_type/:observable_value/sightings"
       (let [{status :status
              sightings :parsed-body}
             (GET app
                  "ctia/ip/10.0.0.1/sightings"
                  :headers {"Authorization" "45c1f5e3f05d0"})]
         (is (= 200 status))
         (is (= #{"sighting-1" "sighting-3"}
                (set (mapcat :external_ids sightings)))))))))
