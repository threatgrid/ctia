(ns ctia.printers
  (:require [clojure.instant :as i])
  (:import org.joda.time.DateTime))

;; Written by Alex Baranosky
;; Copied from https://gist.github.com/ragnard/4738185
;; Converts DateTime to #inst when printing

(defmethod print-method org.joda.time.DateTime
  [^org.joda.time.DateTime d ^java.io.Writer w]
  (#'i/print-date (java.util.Date. (.getMillis d)) w))

(defmethod print-dup org.joda.time.DateTime
  [^org.joda.time.DateTime d ^java.io.Writer w]
  (#'i/print-date (java.util.Date. (.getMillis d)) w))
