(ns ctia.lib.es.slice
  (:require [schema.core :as s]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.coerce :as coerce]
            [ctia.lib.es.index :refer [ESConnState
                                       cached-create-aliased-index!
                                       cached-create-filtered-alias!]]))

(def index-name-tf "YYYY.MM.dd.HH.mm")
(def index-time-formatter (tf/formatter index-name-tf))
(def date-range-formatter (tf/formatters :date-time))

(s/defschema SliceProps
  {:name s/Str
   :granularity s/Keyword
   :strategy s/Keyword
   (s/optional-key :filter) {s/Any s/Any}})

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

(s/defn slice-time :- java.util.Date
  [event-time :- java.util.Date
   granularity :- s/Keyword]

  (let [parsed (coerce/from-date event-time)
        year (t/year parsed)
        month (t/month parsed)
        day (t/day parsed)
        hour (t/hour parsed)
        minute (t/minute parsed)]

    (-> (condp = granularity
          :week (-> (t/date-time year month day)
                    (.dayOfWeek)
                    (.withMinimumValue))
          :minute (t/date-time year month day hour minute)
          :hour   (t/date-time year month day hour)
          :day    (t/date-time year month day)
          :month  (t/date-time year month)
          :year   (t/date-time year))

        (coerce/to-date))))

(s/defn slice-time-filter :- {s/Any s/Any}
  "make the filter part of an index slice"
  [time :- java.util.Date
   granularity :- s/Keyword]

  (let [dt (format-date-time time)
        modifier (condp = granularity
                   :minute "||+1m"
                   :hour   "||+1H"
                   :day    "||+1d"
                   :week   "||+1w"
                   :month  "||+1M"
                   :year   "||+1Y")]

    {:range
     {:timestamp {:gte dt
                  :lt (str dt modifier)}}}))

(s/defn get-slice-props :- SliceProps
  "make slice properties from
   a date an index name and config"
  [t :- java.util.Date
   state :- ESConnState]

  (let [slice-config (get-in state [:props :slicing])
        granularity (:granularity slice-config)
        rounded-time (slice-time t granularity)
        ts (format-index-time rounded-time)
        f  (slice-time-filter rounded-time granularity)]

    (merge slice-config
           {:filter f
            :name (str (:index state) "_" ts)})))

(defn create-slice! [state slice-props]
  (condp = (:strategy slice-props)
    :aliased-index
    (cached-create-aliased-index!
     state
     (:name slice-props))

    :filtered-alias
    (cached-create-filtered-alias!
     state
     (:name slice-props)
     (:name slice-props)
     (:filter slice-props))
    (throw (Exception. "Unknown slicing strategy"))))
