(ns ctia.lib.es.slice
  (:require [clojure.core.memoize :as memo]
            [schema.core :as s]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.coerce :as coerce]
            [ctia.lib.es.index :as index :refer [ESConnState]]))

(def alias-create-cache-ttl-ms (* 1000 60 5))
(def index-name-tf "YYYY-MM-dd-HH-mm")
(def index-time-formatter (tf/formatter index-name-tf))
(def date-range-formatter (tf/formatters :date-time))

(s/defn format-index-time :- s/Str
  [t :- java.util.Date]

  (->> t
       (coerce/from-date)
       (tf/unparse index-time-formatter)))

(s/defn format-date-time :- s/Str
  [t :- java.util.Date]

  (->> t
       (coerce/from-date)
       (tf/unparse date-range-formatter)))

(s/defschema SliceProps
  {:name s/Str
   :filter {s/Any s/Any}})

(s/defn slice-time :- java.util.Date
  [event-time :- java.util.Date
   slice-config :- s/Keyword]

  (let [parsed (coerce/from-date event-time)
        year (t/year parsed)
        month (t/month parsed)
        day (t/day parsed)
        hour (t/hour parsed)
        minute (t/minute parsed)]

    (-> (condp = slice-config
          :minute (t/date-time year month day hour minute)
          :hour   (t/date-time year month day hour)
          :day    (t/date-time year month day)
          :month  (t/date-time year month)
          :year   (t/date-time year))

        (coerce/to-date))))

(s/defn slice-time-filter :- {s/Any s/Any}
  "make the filter part of an index slice"
  [time :- java.util.Date
   slice-config :- s/Keyword]

  (let [slice-date-time (format-date-time time)
        bound (condp = slice-config
                :minute (str slice-date-time "||+1m")
                :hour (str slice-date-time   "||+1H")
                :day (str slice-date-time    "||+1d")
                :month (str slice-date-time  "||+1M")
                :year (str slice-date-time   "||+1Y"))]

    {:range {:timestamp {:gte slice-date-time
                         :lt bound}}}))

(s/defn date->slice-props :- SliceProps
  "make slice properties from
   a date an index name and config"
  [t :- java.util.Date
   index-name :- s/Str
   time-slice-conf :- s/Keyword]

  (let [rounded-time (slice-time t time-slice-conf)
        ts (format-index-time rounded-time)
        f  (slice-time-filter rounded-time time-slice-conf)]
    {:filter f
     :name (str index-name "_" ts)}))

(s/defn create-slice-alias!
  "create an index alias for a slice"

  [state :- ESConnState
   index-slice-name :- s/Str
   index-slice-filter :- {s/Any s/Any}]

  (index/create-alias!
   (:conn state)
   (:index state)
   index-slice-name
   index-slice-name
   index-slice-filter))

(s/defn create-index-alias!
  "create an index with an alias for a slice"

  [state :- ESConnState
   index-slice-name :- s/Str
   index-slice-filter :- {s/Any s/Any}]

  (index/create!
   (:conn state)
   index-slice-name
   (:mapping state))

  (index/create-alias!
   (:conn state)
   index-slice-name
   (:index state)
   index-slice-name
   index-slice-filter))


(def memoized-create-slice-alias!
  (memo/ttl create-slice-alias!
            :ttl/threshold
            alias-create-cache-ttl-ms))

(def memoized-create-index-alias!
  (memo/ttl create-index-alias!
            :ttl/threshold
            alias-create-cache-ttl-ms))
