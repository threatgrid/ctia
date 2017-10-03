(ns ctia.http.routes.observable.sightings-incidents-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mht]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.properties :refer [properties]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [get make-id post]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]
            [ctim.domain.id :as id]))

(use-fixtures :once (join-fixtures [mht/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    helpers/fixture-properties:events-enabled
                                    whoami-helpers/fixture-server]))

(deftest-for-each-store test-observable-sightings-incidents
  (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (let [http-show (get-in @properties [:ctia :http :show])
        sighting-1-id (make-id :sighting)
        sighting-2-id (make-id :sighting)
        sighting-3-id (make-id :sighting)
        judgement-1-id (make-id :judgement)
        incident-1-id (make-id :incident)
        incident-2-id (make-id :incident)
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
            (post "ctia/sighting"
                  :body {:id (:short-id sighting-1-id)
                         :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                         :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                         :observables [observable-1]
                         :external_ids ["sighting-1"]}
                  :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 201 status))))

    ;; This sighting should not be matched (no incident relationship)
    (testing "test setup: create sighting-2"
      (let [{status :status}
            (post "ctia/sighting"
                  :body {:id (:short-id sighting-2-id)
                         :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                         :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                         :observables [observable-1]
                         :external_ids ["sighting-2"]}
                  :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 201 status))))

    ;; This sighting should not be matched (different observable)
    (testing "test setup: create sighting-3"
      (let [{status :status}
            (post "ctia/sighting"
                  :body {:id (:short-id sighting-3-id)
                         :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                         :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                         :observables [observable-2]
                         :external_ids ["sighting-3"]}
                  :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 201 status))))

    ;; This judgement should not be matched (it isn't an incident)
    (testing "test setup: create judgement-1"
      (let [{status :status}
            (post "ctia/judgement"
                  :body {:observable observable-1
                         :source "source"
                         :priority 99
                         :confidence "High"
                         :severity "Medium"
                         :external_ids ["judgement-1"]}
                  :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 201 status))))


    ;; This incident should be found based on the observable/relationship
    (testing "test setup: create incident-1"
      (let [{status :status}
            (post "ctia/incident"
                  :body {:id (:short-id incident-1-id)
                         :confidence "High"
                         :external_ids ["incident-1"]}
                  :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 201 status))))

    ;; This incident should not be found
    (testing "test setup: create incident-1"
      (let [{status :status}
            (post "ctia/incident"
                  :body {:id (:short-id incident-2-id)
                         :confidence "High"
                         :external_ids ["incident-2"]}
                  :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 201 status))))

    ;; This is the relationship that should be matched
    (testing (str "test setup: create relationship-1 so that sighting-1 is a "
                  "member of incident-1")
      (let [{status :status}
            (post "ctia/relationship"
                  :body {:id (:short-id relationship-1-id)
                         :source_ref (id/long-id sighting-1-id)
                         :relationship_type "member-of"
                         :target_ref (id/long-id incident-1-id)
                         :external_ids ["relationship-1"]}
                  :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 201 status))))

    ;; This relationship should not be matched
    (testing (str "test setup: create relationship-2 so that sighting-3 is a "
                  "member of incident-2")
      (let [{status :status}
            (post "ctia/relationship"
                  :body {:id (:short-id relationship-2-id)
                         :source_ref (id/long-id sighting-3-id)
                         :relationship_type "member-of"
                         :target_ref (id/long-id incident-2-id)
                         :external_ids ["relationship-2"]}
                  :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 201 status))))

    ;; This relationship should not be matched
    (testing (str "test setup: create relationship-3 so that sighting-3 is "
                  "based on judgement-1")
      (let [{status :status}
            (post "ctia/relationship"
                  :body {:id (:short-id relationship-3-id)
                         :source_ref (id/long-id sighting-3-id)
                         :relationship_type "based-on"
                         :target_ref (id/long-id judgement-1-id)
                         :external_ids ["relationship-3"]}
                  :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 201 status))))

    (testing "GET /:observable_type/:observable_value/sightings/incidents"
      (let [{status :status
             incident-ids :parsed-body}
            (get (str "ctia/" (:type observable-1) "/" (:value observable-1)
                      "/sightings/incidents")
                 :headers {"Authorization" "45c1f5e3f05d0"})]
        (is (= 200 status))
        (is (= #{(id/long-id incident-1-id)}
               (set incident-ids)))))))
