(ns ^{:doc "Work with java.util.Date objects"}
    ctia.lib.time
    (:require [clj-time.coerce :as time-coerce]
              [clj-time.core :as time]
              [clj-time.format :as time-format]
              [clj-time.coerce :as coerce])
    (:import java.sql.Time
             java.util.Date
             org.joda.time.DateTime))

(defn timestamp
  ([]
   (Date.))
  ([time-str]
   (if (nil? time-str)
     (timestamp)
     (time-coerce/to-date
      (time-format/parse (time-format/formatters :date-time)
                         time-str)))))

(def now timestamp)

(def default-expire-date (time-coerce/to-date (time/date-time 2525 1 1)))

(defn- coerce-to-datetime [d]
  (cond
    (instance? DateTime d) d
    (instance? Date d) (coerce/from-date d)))

(defn- coerce-to-date [d]
  (instance? Date d) d
  (instance? DateTime d) (coerce/to-date d))

(defn after?
  "like clj-time.core/after? but uses java.util.Date"
  [left right]
  (time/after? (coerce-to-datetime left)
               (coerce-to-datetime right)))

(defn sql-now []
  (coerce/to-sql-time (time/now)))

(defn to-sql-time [d]
  (time-coerce/to-sql-time (coerce-to-datetime d)))

(defn from-sql-time [d]
  (coerce-to-date (coerce/from-sql-time d)))

(defn plus-n-weeks [n]
  (time/plus (time/weeks n)))
