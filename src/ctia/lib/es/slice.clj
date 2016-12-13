(ns ctia.lib.es.slice
  (:require [ctia.lib.es.schemas :refer [SliceProperties
                                         DateRangeFilter]]
            [clj-momo.lib.time :refer [format-date-time
                                       format-index-time
                                       round-date]]
            [schema.core :as s]))

(s/defn slice-name :- s/Str
  "make a slice name composed of an index name and a timestamp"
  [index :- s/Str
   ts :- s/Str]
  (str index "_" ts))

(s/defn slice-time-filter :- DateRangeFilter
  "make the filter part of an index slice"
  [time :- java.util.Date
   granularity :- s/Keyword]

  (let [dt (format-date-time time)
        modifier (case granularity
                   :minute "||+1m"
                   :hour   "||+1H"
                   :day    "||+1d"
                   :week   "||+1w"
                   :month  "||+1M"
                   :year   "||+1Y")
        range {:gte dt
               :lt (str dt modifier)}]

    {:range {:timestamp range}}))

(s/defn get-slice-props :- SliceProperties
  "make slice properties from a date an index name and config"
  [t :- java.util.Date
   state]

  (let [slice-config (get-in state [:props :slicing])
        granularity (:granularity slice-config)
        rounded-time (round-date t granularity)
        ts (format-index-time rounded-time)
        f  (slice-time-filter rounded-time granularity)]

    (merge slice-config
           {:filter f
            :name (slice-name (:index state) ts)})))
