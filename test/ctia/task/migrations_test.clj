(ns ctia.task.migrations-test
  (:require [clojure.test :refer [deftest is]]
            [ctia.task.migrations :as sut]))

(deftest add-groups-test
  (is (= (transduce sut/add-groups conj [{}])
         [{:groups ["tenzin"]}]))
  (is (= (transduce sut/add-groups conj [{:groups []}])
         [{:groups ["tenzin"]}]))
  (is (= (transduce sut/add-groups conj [{:groups ["foo"]}])
         [{:groups ["foo"]}])))

(deftest fix-end-time-test
  (is (= (transduce sut/fix-end-time conj [{}]) [{}]))
  (is (= (transduce sut/fix-end-time conj
                    [{:valid_time
                      {:start_time "foo"}}])
         [{:valid_time
           {:start_time "foo"}}]))
  (is (= (transduce sut/fix-end-time conj
                    [{:valid_time
                      {:end_time #inst "2535-01-01T00:00:00.000-00:00"}}])
         [{:valid_time
           {:end_time #inst "2525-01-01T00:00:00.000-00:00"}}]))

  (is (= (transduce sut/fix-end-time conj
                    [{:valid_time
                      {:end_time #inst "2524-01-01T00:00:00.000-00:00"}}])
         [{:valid_time
           {:end_time #inst "2524-01-01T00:00:00.000-00:00"}}])))

(deftest pluralize-target-test
  (is (= (transduce sut/pluralize-target conj
                    [{:type "sighting"
                      :target []}]) [{:type "sighting"
                                      :targets []}])))

(deftest target-observed_time-test
  (is (= (transduce sut/target-observed_time conj
                    [{:type "sighting"
                      :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                      :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                      :target {}}])
         [{:type "sighting"
           :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2016-02-11T00:40:48.212-00:00"}
           :target {:observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                    :end_time #inst "2016-02-11T00:40:48.212-00:00"}}}])))

(deftest test-zero-4-twenty-height-sighting-targets
  (is (= (transduce (:0.4.28 sut/available-migrations) conj
                    '({:schema_version "0.4.24",
                       :observables [{:value "22.11.22.11", :type "ip"}],
                       :type "sighting",
                       :created "2018-03-15T13:40:33.786Z",
                       :modified "2018-03-15T13:40:33.786Z",
                       :id "sighting-185a01dc-ac0b-4c98-950f-772f85285f63",
                       :count 1,
                       :tlp "green",
                       :target {:type "endpoint.sensor",
                                :observables [{:value "33.231.32.43", :type "ip"}],
                                :os "ubuntu"},
                       :groups ["4ca63e42-4a71-4c85-9a28-06afac0c2273"],
                       :timestamp "2018-03-15T02:36:00.632Z",
                       :confidence "Unknown",
                       :observed_time {:start_time "2018-03-15T02:36:00.632Z", :end_time "2018-03-15T02:36:00.632Z"},
                       :observables_hash ["ip:22.11.22.11"],
                       :owner "b3eab96a-ed74-4195-9c2b-f8b3dd447ccd"}
                      {:schema_version "0.4.24",
                       :observables [{:value "22.11.22.11", :type "ip"}],
                       :type "sighting",
                       :created "2018-03-15T03:26:27.002Z",
                       :modified "2018-03-15T03:26:27.002Z",
                       :id "sighting-130d84b7-0794-4b94-b9aa-279162d17a47",
                       :count 1,
                       :tlp "green",
                       :target {:type "endpoint.sensor",
                                :observables [{:value "33.231.32.43", :type "ip"}],
                                :os "ubuntu"},
                       :groups ["4ca63e42-4a71-4c85-9a28-06afac0c2273"],
                       :timestamp "2018-03-15T02:36:00.632Z",
                       :confidence "Unknown",
                       :observed_time {:start_time "2018-03-15T02:36:00.632Z",
                                       :end_time "2018-03-15T02:36:00.632Z"},
                       :observables_hash ["ip:22.11.22.11"],
                       :owner "b3eab96a-ed74-4195-9c2b-f8b3dd447ccd"}))
         [{:schema_version "0.4.28",
           :observables [{:value "22.11.22.11", :type "ip"}],
           :type "sighting",
           :created "2018-03-15T13:40:33.786Z",
           :modified "2018-03-15T13:40:33.786Z",
           :targets
           [{:type "endpoint.sensor",
             :observables [{:value "33.231.32.43", :type "ip"}],
             :os "ubuntu",
             :observed_time
             {:start_time "2018-03-15T02:36:00.632Z",
              :end_time "2018-03-15T02:36:00.632Z"}}],
           :id "sighting-185a01dc-ac0b-4c98-950f-772f85285f63",
           :count 1,
           :tlp "green",
           :groups ["4ca63e42-4a71-4c85-9a28-06afac0c2273"],
           :timestamp "2018-03-15T02:36:00.632Z",
           :confidence "Unknown",
           :observed_time
           {:start_time "2018-03-15T02:36:00.632Z",
            :end_time "2018-03-15T02:36:00.632Z"},
           :observables_hash ["ip:22.11.22.11"],
           :owner "b3eab96a-ed74-4195-9c2b-f8b3dd447ccd"}
          {:schema_version "0.4.28",
           :observables [{:value "22.11.22.11", :type "ip"}],
           :type "sighting",
           :created "2018-03-15T03:26:27.002Z",
           :modified "2018-03-15T03:26:27.002Z",
           :targets
           [{:type "endpoint.sensor",
             :observables [{:value "33.231.32.43", :type "ip"}],
             :os "ubuntu",
             :observed_time
             {:start_time "2018-03-15T02:36:00.632Z",
              :end_time "2018-03-15T02:36:00.632Z"}}],
           :id "sighting-130d84b7-0794-4b94-b9aa-279162d17a47",
           :count 1,
           :tlp "green",
           :groups ["4ca63e42-4a71-4c85-9a28-06afac0c2273"],
           :timestamp "2018-03-15T02:36:00.632Z",
           :confidence "Unknown",
           :observed_time
           {:start_time "2018-03-15T02:36:00.632Z",
            :end_time "2018-03-15T02:36:00.632Z"},
           :observables_hash ["ip:22.11.22.11"],
           :owner "b3eab96a-ed74-4195-9c2b-f8b3dd447ccd"}])))
