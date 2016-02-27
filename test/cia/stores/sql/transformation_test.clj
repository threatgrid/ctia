(ns cia.stores.sql.transformation-test
  (:require [cia.stores.sql.transformation :as t]
            [cia.test-helpers.core]
            [clj-time.coerce :as coerce]
            [clojure.test :refer [deftest is]])
  (:import org.joda.time.DateTime))

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
