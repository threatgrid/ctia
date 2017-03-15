(ns ctia.http.routes.observable.verdict.es.core
  (:require [clj-time
             [core :as time]
             [format :as format]]))

(def ^:private formatter
  (format/formatters :date-time))

(defn format-date-time [dt]
  (format/unparse formatter dt))

(defn now []
  (-> (time/now)
      format-date-time))

(def recently
  (memoize now))

(def one-month-ago
  (memoize
   (fn []
     (-> (time/now)
         (time/minus
          (time/months 1))
         format-date-time))))

(def two-months-ago
  (memoize
   (fn []
     (-> (time/now)
         (time/minus
          (time/months 2))
         format-date-time))))

(def one-day-ago
  (memoize
   (fn []
     (-> (time/now)
         (time/minus
          (time/days 1))
         format-date-time))))

(def two-days-ago
  (memoize
   (fn []
     (-> (time/now)
         (time/minus
          (time/days 2))
         format-date-time))))

(def one-week-ago
  (memoize
   (fn []
     (-> (time/now)
         (time/minus
          (time/weeks 1))
         format-date-time))))

(def two-weeks-ago
  (memoize
   (fn []
     (-> (time/now)
         (time/minus
          (time/weeks 2))
         format-date-time))))

(def after-were-all-dead
  (memoize
   (fn []
     (-> (time/date-time 2525 1 1)
         format-date-time))))
