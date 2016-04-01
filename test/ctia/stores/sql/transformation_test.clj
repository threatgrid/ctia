(ns ctia.stores.sql.transformation-test
  (:require [ctia.lib.time :as time]
            [ctia.stores.sql.transformation :as t]
            [ctia.test-helpers.core]
            [clojure.test :refer [deftest is]])
  (:import java.util.Date))

(deftest drop-nils-test
  (is (= {:a :foo :c :bar}
         (t/drop-nils {:a :foo :b nil :c :bar :d nil}))))

(deftest to-db-observable
  (is (= {:foo :bar :observable_type "type" :observable_value "value"}
       (t/to-db-observable
        {:foo :bar :observable {:type "type" :value "value"}}))))

(deftest to-schema-observable
  (is (= {:foo :bar :observable {:type "type" :value "value"}}
         (t/to-schema-observable
          {:foo :bar :observable_type "type" :observable_value "value"}))))

(deftest to-db-valid-time
  (is (= {:foo :bar :valid_time_start_time "start" :valid_time_end_time "end"}
         (t/to-db-valid-time
          {:foo :bar :valid_time {:start_time "start" :end_time "end"}}))))

(deftest to-schema-valid-time
  (is (= {:foo :bar :valid_time {:start_time "start" :end_time "end"}}
         (t/to-schema-valid-time
          {:foo :bar :valid_time_start_time "start" :valid_time_end_time "end"}))))

(deftest dates-to-sqltimes-test
  (let [now (time/now)
        sql-now (time/to-sql-time now)]
    (is (deep=
         [{:foo :bar}
          {:a :a :b sql-now :c :c}
          {:a sql-now :b {:c sql-now :d {:e sql-now}}}]
         (t/dates-to-sqltimes
          [{:foo :bar}
           {:a :a :b now :c :c}
           {:a now :b {:c now :d {:e now}}}])))))

(deftest sqltimes-to-dates-test
  (let [now (time/now)
        sql-now (time/to-sql-time now)]
    (is (deep=
         [{:foo :bar}
          {:a :a :b now :c :c}
          {:a now :b {:c now :d {:e now}}}]
         (t/sqltimes-to-dates
          [{:foo :bar}
           {:a :a :b sql-now :c :c}
           {:a sql-now :b {:c sql-now :d {:e sql-now}}}])))))

(def relationship-example
  {:entity-relationship-key :indicators
   :relationship-reference-key :indicator_id
   :entity-id-key :judgement_id
   :other-id-key :indicator_id})

(deftest db-relationship->schema-relationship
  (let [sut (t/db-relationship->schema-relationship relationship-example)]
    (is (= {:confidence "confidence"
            :source "source"
            :relationship "relationship"
            :indicator_id "indicator_id"}
           (sut
            {:confidence "confidence"
             :source "source"
             :relationship "relationship"
             :judgement_id "judgement_id"
             :indicator_id "indicator_id"})))

    (is (= {:indicator_id "indicator_id"}
           (sut
            {:judgement_id "judgement_id"
             :indicator_id "indicator_id"})))))

(deftest entities->db-relationships
  (let [sut (t/entities->db-relationships relationship-example)]
    (is (= [{:confidence "confidence"
             :source "source"
             :relationship "relationship"
             :judgement_id "judgement_id"
             :indicator_id "indicator_id"}
            {:judgement_id "judgement_id"
             :indicator_id "indicator_id"}]
           (sut
            [{:id "judgement_id"
              :indicators [{:confidence "confidence"
                            :source "source"
                            :relationship "relationship"
                            :indicator_id "indicator_id"}]}
             {:id "judgement_id"
              :indicators [{:indicator_id "indicator_id"}]}])))))

(deftest filter-map->where-map
  (is (= {:observable_type "type"
          :observable_value "value"}
         (t/filter-map->where-map
          {[:observable :type] "type"
           [:observable :value] "value"}))))
