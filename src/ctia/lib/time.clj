(ns ctia.lib.time
  (:require [clj-time.coerce :as time-coerce]
            [clj-time.core :as time]
            [clj-time.format :as time-format])
  (:import java.util.Date))

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
