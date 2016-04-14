(ns ^{:doc "Work with java.util.Date objects"}
    ctia.lib.time
    (:require [clj-time.core :as time]
              [clj-time.format :as time-format])
    (:import [java.sql Time Timestamp]
             [java.util Date]
             [org.joda.time DateTime DateTimeZone]))

(defn- datetime-from-long [^Long millis]
  (DateTime. millis ^DateTimeZone (DateTimeZone/UTC)))

(defn- coerce-to-datetime [d]
  (cond
    (instance? DateTime d) d
    (instance? Date d) (datetime-from-long (.getTime d))
    (instance? Timestamp d) (datetime-from-long (.getTime d))))

(defn- coerce-to-date [d]
  (cond
    (instance? Date d) d
    (instance? DateTime d) (Date. (.getMillis d))
    (instance? Timestamp d) (Date. (.getTime d))))

(defn- coerce-to-sql-time [d]
  (cond
    (instance? Timestamp d) d
    (instance? Date d) (Timestamp. (.getTime d))
    (instance? DateTime d) (Timestamp. (.getMillis d))))

(defn timestamp
  ([]
   (Date.))
  ([time-str]
   (if (nil? time-str)
     (timestamp)
     (coerce-to-date
      (time-format/parse (time-format/formatters :date-time)
                         time-str)))))

(def now timestamp)

(def default-expire-date (coerce-to-date (time/date-time 2525 1 1)))

(defn after?
  "like clj-time.core/after? but uses java.util.Date"
  [left right]
  (time/after? (coerce-to-datetime left)
               (coerce-to-datetime right)))

(defn sql-now []
  (coerce-to-sql-time (now)))

(def to-sql-time coerce-to-sql-time)

(def from-sql-time coerce-to-date)

(defn plus-n-weeks [n]
  (time/plus (time/weeks n)))
