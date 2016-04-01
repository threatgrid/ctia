(ns ^{:doc "Pre-compiled paths for specter"}
    ctia.lib.specter.paths
  (:require [com.rpl.specter :refer :all])
  (:import java.sql.Timestamp
           java.util.Date))

(def all-last-all
  "Path for selecting nested maps like:
     In: {:foo [{:map 1} {:map 2}]
          :bar [{:map 3} {:map 4}]}
     Out: [{:map 1} {:map 2}]"
  (comp-paths ALL LAST ALL))

(def all-last
  "Path for selecting map values:
     In: {1 [{:a 1} {:a 2}]
          2 [{:a 3} {:a 4}]}
     Out: [[{:a 1} {:a 2}]
           [{:a 3} {:a 4}]]"
  (comp-paths ALL LAST))

(def all-first
  "Path for selecting map keys:
     In: {[:observable :type] ip
          [:observable :value] 10.0.0.1}
     Out: [[:observable :type]
           [:observable :value]]"
  (comp-paths ALL FIRST))

(defn date? [x]
  (instance? Date x))

(def walk-dates
  (comp-paths (walker date?)))

(defn sqltime? [x]
  (instance? Timestamp x))

(def walk-sqltimes
  (comp-paths (walker sqltime?)))
