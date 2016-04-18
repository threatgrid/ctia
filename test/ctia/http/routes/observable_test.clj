(ns ctia.http.routes.judgement-test
  (:refer-clojure :exclude [get])
  (:require
   [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
   [ctia.test-helpers.core :refer [delete get post put] :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [deftest-for-each-store]]
   [ctia.test-helpers.auth :refer [all-capabilities]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-get-things-by-observable-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (let [{{judgement-1-id :id} :parsed-body
         judgement-1-status :status}
        (post "ctia/judgement"
              :body {:observable {:value "1.2.3.4"
                                  :type "ip"}
                     :disposition 2
                     :source "judgement 1"
                     :priority 100
                     :severity 100
                     :confidence "Low"
                     :indicators []
                     :valid_time {:start_time "2016-02-01T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {sighting-1-status :status
         {sighting-1-id :id} :parsed-body}
        (post "ctia/sighting"
              :body {:timestamp "2016-02-01T00:00:00.000-00:00"
                     :source "foo"
                     :confidence "Medium"
                     :description "sighting 1"}
              :headers {"api_key" "45c1f5e3f05d0"})

        {sighting-2-status :status
         {sighting-2-id :id} :parsed-body}
        (post "ctia/sighting"
              :body {:timestamp "2016-02-01T12:00:00.000-00:00"
                     :source "bar"
                     :confidence "High"
                     :description "sighting 2"}
              :headers {"api_key" "45c1f5e3f05d0"})

        {indicator-1-status :status
         {indicator-1-id :id} :parsed-body}
        (post "ctia/indicator"
              :body {:title "indicator"
                     :sightings [{:sighting_id sighting-1-id}
                                 {:sighting_id sighting-2-id}]
                     :description "indicator 1"
                     :producer "producer"
                     :indicator_type ["C2" "IP Watchlist"]
                     :valid_time {:end_time "2016-02-12T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {judgement-1-update-status :status}
        (post (str "ctia/judgement/" judgement-1-id "/indicator")
              :body {:indicator_id indicator-1-id}
              :headers {"api_key" "45c1f5e3f05d0"})

        {{judgement-2-id :id} :parsed-body
         judgement-2-status :status}
        (post "ctia/judgement"
              :body {:observable {:value "10.0.0.1"
                                  :type "ip"}
                     :disposition 2
                     :source "judgement 2"
                     :priority 100
                     :severity 100
                     :confidence "High"
                     :indicators []
                     :valid_time {:start_time "2016-02-01T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {sighting-3-status :status
         {sighting-3-id :id} :parsed-body}
        (post "ctia/sighting"
              :body {:timestamp "2016-02-04T12:00:00.000-00:00"
                     :source "spam"
                     :confidence "None"
                     :description "sighting 3"}
              :headers {"api_key" "45c1f5e3f05d0"})

        {indicator-2-status :status
         {indicator-2-id :id} :parsed-body}
        (post "ctia/indicator"
              :body {:title "indicator"
                     :sightings [{:sighting_id sighting-3-id}]
                     :description "indicator 2"
                     :producer "producer"
                     :indicator_type ["C2" "IP Watchlist"]
                     :valid_time {:start_time "2016-01-12T00:00:00.000-00:00"
                                  :end_time "2016-02-12T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {judgement-2-update-status :status}
        (post (str "ctia/judgement/" judgement-2-id "/indicator")
              :body {:indicator_id indicator-2-id}
              :headers {"api_key" "45c1f5e3f05d0"})

        {{judgement-3-id :id} :parsed-body
         judgement-3-status :status}
        (post "ctia/judgement"
              :body {:observable {:value "10.0.0.1"
                                  :type "ip"}
                     :disposition 2
                     :source "judgement 3"
                     :priority 100
                     :severity 100
                     :confidence "Low"
                     :indicators []
                     :valid_time {:start_time "2016-02-01T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {sighting-4-status :status
         {sighting-4-id :id} :parsed-body}
        (post "ctia/sighting"
              :body {:timestamp "2016-02-05T01:00:00.000-00:00"
                     :source "foo"
                     :confidence "High"
                     :description "sighting 4"}
              :headers {"api_key" "45c1f5e3f05d0"})

        {sighting-5-status :status
         {sighting-5-id :id} :parsed-body}
        (post "ctia/sighting"
              :body {:timestamp "2016-02-05T02:00:00.000-00:00"
                     :source "bar"
                     :confidence "Low"
                     :description "sighting 5"}
              :headers {"api_key" "45c1f5e3f05d0"})

        {indicator-3-status :status
         {indicator-3-id :id} :parsed-body}
        (post "ctia/indicator"
              :body {:title "indicator"
                     :sightings [{:sighting_id sighting-4-id}
                                 {:sighting_id sighting-5-id}]
                     :description "indicator 3"
                     :producer "producer"
                     :indicator_type ["C2" "IP Watchlist"]
                     :valid_time {:start_time "2016-01-11T00:00:00.000-00:00"
                                  :end_time "2016-02-11T00:00:00.000-00:00"}}
              :headers {"api_key" "45c1f5e3f05d0"})

        {judgement-3-update-status :status}
        (post (str "ctia/judgement/" judgement-3-id "/indicator")
              :body {:indicator_id indicator-3-id}
              :headers {"api_key" "45c1f5e3f05d0"})]

    (testing "With successful test setup"
      (is (= 200 judgement-1-status))
      (is (= 200 sighting-1-status))
      (is (= 200 sighting-2-status))
      (is (= 200 indicator-1-status))
      (is (= 200 judgement-1-update-status))
      (is (= 200 judgement-2-status))
      (is (= 200 sighting-3-status))
      (is (= 200 indicator-2-status))
      (is (= 200 judgement-2-update-status))
      (is (= 200 judgement-3-status))
      (is (= 200 sighting-4-status))
      (is (= 200 sighting-5-status))
      (is (= 200 indicator-3-status))
      (is (= 200 judgement-3-update-status)))

    (testing "GET /ctia/:observable_type/:observable_value/judgements"
      (let [{status :status
             judgements :parsed-body}
            (get "ctia/ip/10.0.0.1/judgements"
                 :headers {"api_key" "45c1f5e3f05d0"})]
        (is (= 200 status))
        (is (deep=
             #{{:id judgement-2-id
                :type "judgement"
                :observable {:value "10.0.0.1"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :source "judgement 2"
                :priority 100
                :severity 100
                :confidence "High"
                :indicators [{:indicator_id indicator-2-id}]
                :valid_time {:start_time #inst "2016-02-01T00:00:00.000-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :owner "foouser"}
               {:id judgement-3-id
                :type "judgement"
                :observable {:value "10.0.0.1"
                             :type "ip"}
                :disposition 2
                :disposition_name "Malicious"
                :source "judgement 3"
                :priority 100
                :severity 100
                :confidence "Low"
                :indicators [{:indicator_id indicator-3-id}]
                :valid_time {:start_time #inst "2016-02-01T00:00:00.000-00:00"
                             :end_time #inst "2525-01-01T00:00:00.000-00:00"}
                :owner "foouser"}}
             (->> judgements
                  (map #(dissoc % :created))
                  set)))))

    (testing "GET /ctia/:observable_type/:observable_value/indicators"
      (let [response (get "ctia/ip/10.0.0.1/indicators"
                          :headers {"api_key" "45c1f5e3f05d0"})
            indicators (:parsed-body response)]
        (is (= 200 (:status response)))

        (is (deep=
             #{{:id indicator-2-id
                :type "indicator"
                :title "indicator"
                :sightings [{:sighting_id sighting-3-id}]
                :description "indicator 2"
                :producer "producer"
                :indicator_type ["C2" "IP Watchlist"]
                :valid_time {:start_time #inst "2016-01-12T00:00:00.000-00:00"
                             :end_time #inst "2016-02-12T00:00:00.000-00:00"}
                :owner "foouser"}
               {:id indicator-3-id
                :type "indicator"
                :title "indicator"
                :sightings [{:sighting_id sighting-4-id}
                            {:sighting_id sighting-5-id}]
                :description "indicator 3"
                :producer "producer"
                :indicator_type ["C2" "IP Watchlist"]
                :valid_time {:start_time #inst "2016-01-11T00:00:00.000-00:00"
                             :end_time #inst "2016-02-11T00:00:00.000-00:00"}
                :owner "foouser"}}
             (->> indicators
                  (map #(dissoc % :created :modified))
                  set)))))



    (testing "GET /ctia/:observable_type/:observable_value/sightings"
      (let [{status :status
             sightings :parsed-body
             :as response}
            (get "ctia/ip/10.0.0.1/sightings"
                 :headers {"api_key" "45c1f5e3f05d0"})]
        (is (= 200 status))
        (is (deep=
             #{{:id sighting-3-id
                :type "sighting"
                :timestamp #inst "2016-02-04T12:00:00.000-00:00"
                :source "spam"
                :confidence "None"
                :description "sighting 3"
                :owner "foouser"}
               {:id sighting-4-id
                :type "sighting"
                :timestamp #inst "2016-02-05T01:00:00.000-00:00"
                :source "foo"
                :confidence "High"
                :description "sighting 4"
                :owner "foouser"}
               {:id sighting-5-id
                :type "sighting"
                :timestamp #inst "2016-02-05T02:00:00.000-00:00"
                :source "bar"
                :confidence "Low"
                :description "sighting 5"
                :owner "foouser"}}
             (->> sightings
                  (map #(dissoc % :created :modified))
                  set)))))))

(deftest-for-each-store test-observable-verdict-route
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "a verdict that doesn't exist is a 404"
    (let [{status :status}
          (get "ctia/ip/10.0.0.1/verdict"
               :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 404 status))))

  (testing "test setup: create a judgement (1)"
    ;; Incorrect observable
    (let [response (post "ctia/judgement"
                         :body {:indicators []
                                :observable {:value "127.0.0.1"
                                             :type "ip"}
                                :disposition 1
                                :source "test"
                                :priority 100
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 200 (:status response)))))

  (testing "test setup: create a judgement (2)"
    ;; Lower priority
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 1
                                :source "test"
                                :priority 90
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 200 (:status response)))))

  (testing "test setup: create a judgement (3)"
    ;; Wrong disposition
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 3
                                :source "test"
                                :priority 99
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 200 (:status response)))))

  (testing "test setup: create a judgement (4)"
    ;; Loses a tie because of its timestamp being later
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 99
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:01:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement-1 (:parsed-body response)]
      (is (= 200 (:status response)))))

  (testing "with a highest-priority judgement"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "10.0.0.1"
                                             :type "ip"}
                                :disposition 2
                                :source "test"
                                :priority 99
                                :severity 100
                                :confidence "Low"
                                :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement-1 (:parsed-body response)]
      (is (= 200 (:status response))) ;; success creating judgement

      (testing "GET /ctia/:observable_type/:observable_value/verdict"
        (let [response (get "ctia/ip/10.0.0.1/verdict"
                            :headers {"api_key" "45c1f5e3f05d0"})
              verdict (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (= {:type "verdict"
                  :disposition 2
                  :disposition_name "Malicious"
                  :judgement_id (:id judgement-1)}
                 verdict)))))))


(deftest-for-each-store test-observable-verdict-route-2
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  ;; This test case catches a bug that was in the atom store
  ;; It tests the code path where priority is equal but dispositions differ
  (testing "test setup: create a judgement (1)"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "string",
                                             :type "device"},
                                :reason_uri "string",
                                :source "string",
                                :disposition 1,
                                :reason "string",
                                :source_uri "string",
                                :priority 99,
                                :severity 50,
                                :valid_time {:start_time "2016-02-12T14:56:26.814-00:00"
                                             :end_time "2016-02-12T14:56:26.719-00:00"}
                                :confidence "Medium"}
                         :headers {"api_key" "45c1f5e3f05d0"})]
      (is (= 200 (:status response)))))
  (testing "with a verdict judgement"
    (let [response (post "ctia/judgement"
                         :body {:observable {:value "10.0.0.1",
                                             :type "ip"},
                                :reason_uri "string",
                                :source "string",
                                :disposition 2,
                                :reason "string",
                                :source_uri "string",
                                :priority 99,
                                :severity 50,
                                :valid_time {:start_time "2016-02-12T14:56:26.814-00:00"}
                                :confidence "Medium"}
                         :headers {"api_key" "45c1f5e3f05d0"})
          judgement (:parsed-body response)]
      (is (= 200 (:status response)))

      (testing "GET /ctia/:observable_type/:observable_value/verdict"
        (with-redefs [ctia.lib.time/now (constantly (ctia.lib.time/timestamp "2016-02-12T15:42:58.232-00:00"))]
          (let [response (get "ctia/ip/10.0.0.1/verdict"
                              :headers {"api_key" "45c1f5e3f05d0"})
                verdict (:parsed-body response)]
            (is (= 200 (:status response)))
            (is (= {:type "verdict"
                    :disposition 2
                    :disposition_name "Malicious"
                    :judgement_id (:id judgement)}
                   verdict))))))))

