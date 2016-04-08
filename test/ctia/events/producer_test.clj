(ns ctia.events.producer-test
  (:require [ctia.events.producer :refer :all]
            [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.index :as index-helpers]
            [clojure.test :as t]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties]))

(defmacro deftest-for-each-producer [test-name & body]
  `(helpers/deftest-for-each-fixture ~test-name
     {:es-producer (join-fixtures [(index-helpers/fixture-es-producer)
                                   index-helpers/fixture-clean-producer-index
                                   ])}


     ~@body))

(deftest-for-each-producer test-producer-event-create
  (testing "Produce CreatedModel Event"
    (let [event {:owner "test-owner"
                 :timestamp (java.util.Date.)
                 :id "test-id"
                 :http-params {}
                 :type "CreatedModel"
                 :model {:a 1
                         :b 2
                         :c 3}}

          produced (produce event)]

      (is string? produced))))


(deftest-for-each-producer test-producer-event-update
  (testing "Produce UpdatedModel Event"
    (let [event {:owner "test-owner"
                 :timestamp (java.util.Date.)
                 :id "test-id"
                 :http-params {}
                 :type "UpdatedModel"
                 :model {:a 1
                         :b 2
                         :c 3}
                 :fields [[:f1 "delete" {"x" "y"}]
                          [:f2 "assert" {"x" "y"}]]}

          produced (produce event)]

      (is string? produced))))

(deftest-for-each-producer test-producer-event-delete
  (testing "Produce DeletedModel Event"
    (let [event {:owner "test-owner"
                 :timestamp (java.util.Date.)
                 :id "test-id"
                 :http-params {}
                 :type "DeletedModel"
                 :model {:a 1
                         :b 2
                         :c 3}}

          produced (produce event)]

      (is string? produced))))

(deftest-for-each-producer test-producer-event-verdict-change
  (testing "Produce VerdictChange Event"
    (let [event {:owner "test-owner"
                 :timestamp (java.util.Date.)
                 :id "test-id"
                 :http-params {}
                 :type "VerdictChange"
                 :judgement_id "test-judgement-id"
                 :model {:a 1
                         :b 2
                         :c 3}
                 :verdict {:type "verdict"
                           :disposition 2
                           :judgement_id "test-judgement-id"
                           :disposition_name "Malicious"}}

          produced (produce event)]

      (is string? (produce event)))))
