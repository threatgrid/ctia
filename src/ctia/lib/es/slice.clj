(ns ctia.lib.es.slice
  (:require [clj-momo.lib.time :refer [format-date-time
                                       format-index-time
                                       round-date]]
            [ctia.lib.es.index :refer [ESConnState]]
            [schema.core :as s]))

(s/defschema DateRangeFilter
  "a Date range filter for a filtered alias"
  {:range
   {:timestamp {:gte s/Str
                :lt s/Str}}})

(s/defschema SliceProperties
  "Slice configuration properties"
  {:name s/Str
   :granularity s/Keyword
   :strategy s/Keyword
   (s/optional-key :filter) DateRangeFilter})

(s/defn slice-name :- s/Str
  "make a slice name from an index name and a timestamp"
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
   state :- ESConnState]

  (let [slice-config (get-in state [:props :slicing])
        granularity (:granularity slice-config)
        rounded-time (round-date t granularity)
        ts (format-index-time rounded-time)
        f  (slice-time-filter rounded-time granularity)]

    (merge slice-config
           {:filter f
            :name (slice-name (:index state) ts)})))
