(ns ctia.http.routes.observable.sightings-indicators-test
  (:require [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers :refer [GET make-id POST]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.http :refer [app->HTTPShowServices]]
            [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
            [ctim.domain.id :as id]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :once (join-fixtures [validate-schemas
                                    helpers/fixture-properties:events-enabled
                                    whoami-helpers/fixture-server]))

(deftest test-observable-sightings-indicators
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [make-id #(make-id % (app->HTTPShowServices app))

           sighting-1-id (make-id :sighting)
           sighting-2-id (make-id :sighting)
           sighting-3-id (make-id :sighting)
           judgement-1-id (make-id :judgement)
           indicator-1-id (make-id :indicator)
           indicator-2-id (make-id :indicator)
           relationship-1-id (make-id :relationship)
           relationship-2-id (make-id :relationship)
           relationship-3-id (make-id :relationship)
           observable-1 {:type "ip",
                         :value "10.0.0.1"}
           observable-2 {:type "ip"
                         :value "192.168.1.1"}]

       ;; This sighting should be matched
       (testing "test setup: create sighting-1"
         (let [{status :status}
               (POST app
                     "ctia/sighting"
                     :body {:id (id/long-id sighting-1-id)
                            :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                            :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                            :observables [observable-1]
                            :external_ids ["sighting-1"]}
                     :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 201 status))))

       ;; This sighting should not be matched (no indicator relationship)
       (testing "test setup: create sighting-2"
         (let [{status :status}
               (POST app
                     "ctia/sighting"
                     :body {:id (id/long-id sighting-2-id)
                            :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                            :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                            :observables [observable-1]
                            :external_ids ["sighting-2"]}
                     :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 201 status))))

       ;; This sighting should not be matched (different observable)
       (testing "test setup: create sighting-3"
         (let [{status :status}
               (POST app
                     "ctia/sighting"
                     :body {:id (id/long-id sighting-3-id)
                            :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                            :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                            :observables [observable-2]
                            :external_ids ["sighting-3"]}
                     :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 201 status))))

       ;; This judgement should not be matched (it isn't an incident)
       (testing "test setup: create judgement-1"
         (let [{status :status}
               (POST app
                     "ctia/judgement"
                     :body {:observable observable-1
                            :source "source"
                            :priority 99
                            :confidence "High"
                            :severity "Medium"
                            :external_ids ["judgement-1"]}
                     :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 201 status))))

       ;; This indicator should be found based on the observable/relationship
       (testing "test setup: create indicator-1"
         (let [{status :status}
               (POST app
                     "ctia/indicator"
                     :body {:id (id/long-id indicator-1-id)
                            :producer "producer"
                            :external_ids ["indicator-1"]}
                     :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 201 status))))

       ;; This indicator should not be found
       (testing "test setup: create indicator-2"
         (let [{status :status}
               (POST app
                     "ctia/indicator"
                     :body {:id (id/long-id indicator-2-id)
                            :producer "producer"
                            :external_ids ["indicator-2"]}
                     :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 201 status))))

       ;; This is the relationship that should be matched
       (testing (str "test setup: create relationship-1 so that sighting-1 is an "
                     "indication of indicator-1")
         (let [{status :status}
               (POST app
                     "ctia/relationship"
                     :body {:id (id/long-id relationship-1-id)
                            :source_ref (id/long-id sighting-1-id)
                            :relationship_type "sighting-of"
                            :target_ref (id/long-id indicator-1-id)
                            :external_ids ["relationship-1"]}
                     :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 201 status))))

       ;; This relationship should not be matched
       (testing (str "test setup: create relationship-2 so that sighting-3 is an "
                     "indication of indicator-2")
         (let [{status :status}
               (POST app
                     "ctia/relationship"
                     :body {:id (id/long-id relationship-2-id)
                            :source_ref (id/long-id sighting-3-id)
                            :relationship_type "sighting-of"
                            :target_ref (id/long-id indicator-2-id)
                            :external_ids ["relationship-2"]}
                     :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 201 status))))

       ;; This relationship should not be matched
       (testing (str "test setup: create relationship-3 so that sighting-3 is "
                     "based on judgement-1")
         (let [{status :status}
               (POST app
                     "ctia/relationship"
                     :body {:id (id/long-id relationship-3-id)
                            :source_ref (id/long-id sighting-3-id)
                            :relationship_type "based-on"
                            :target_ref (id/long-id judgement-1-id)
                            :external_ids ["relationship-3"]}
                     :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 201 status))))

       (testing "GET /:observable_type/:observable_value/sightings/indicators"
         (let [{status :status
                indicator-ids :parsed-body}
               (GET app
                    (str "ctia/" (:type observable-1) "/" (:value observable-1)
                         "/sightings/indicators")
                    :headers {"Authorization" "45c1f5e3f05d0"})]
           (is (= 200 status))
           (is (= #{(id/long-id indicator-1-id)}
                  (set indicator-ids)))))))))
