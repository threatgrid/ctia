(ns cia.stores.sql.transformation-test
  (:require [cia.stores.sql.transformation :as t]
            [cia.test-helpers.core]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [clojure.test :refer [deftest is]])
  (:import org.joda.time.DateTime))

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

(deftest datetimes-to-sqltimes-test
  (let [now (DateTime.)
        sql-now (coerce/to-sql-time now)]
    (is (deep=
         [{:foo :bar}
          {:a :a :b sql-now :c :c}
          {:a sql-now :b {:c sql-now :d {:e sql-now}}}]
         (t/datetimes-to-sqltimes
          [{:foo :bar}
           {:a :a :b now :c :c}
           {:a now :b {:c now :d {:e now}}}])))))

(deftest sqltimes-to-datetimes-test
  (let [now (time/date-time 2016 2 29 7 6 0)
        sql-now (coerce/to-sql-time now)]
    (is (deep=
         [{:foo :bar}
          {:a :a :b now :c :c}
          {:a now :b {:c now :d {:e now}}}]
         (t/sqltimes-to-datetimes
          [{:foo :bar}
           {:a :a :b sql-now :c :c}
           {:a sql-now :b {:c sql-now :d {:e sql-now}}}])))))

(def relationship-example
  {:entity-relationship-key :indicators
   :relationship-reference-key :indicator
   :entity-id-key :judgement_id
   :other-id-key :indicator_id})

(deftest db-relationship->schema-relationship
  (let [sut (t/db-relationship->schema-relationship relationship-example)]
    (is (= {:confidence "confidence"
            :source "source"
            :relationship "relationship"
            :indicator "indicator_id"}
           (sut
            {:confidence "confidence"
             :source "source"
             :relationship "relationship"
             :judgement_id "judgement_id"
             :indicator_id "indicator_id"})))

    (is (= {:indicator "indicator_id"}
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
                            :indicator "indicator_id"}]}
             {:id "judgement_id"
              :indicators [{:indicator "indicator_id"}]}])))))

(deftest filter-map->where-map
  (is (= {:observable_type "type"
          :observable_value "value"}
         (t/filter-map->where-map
          {[:observable :type] "type"
           [:observable :value] "value"}))))
