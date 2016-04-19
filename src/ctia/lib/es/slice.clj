(ns ctia.lib.es.slice
  (:require [schema.core :as s]
            [ctia.lib.time :refer [format-date-time
                                   format-index-time
                                   round-date]]
            [ctia.lib.es.index :refer [ESConnState
                                       cached-create-aliased-index!
                                       cached-create-filtered-alias!]]))

(s/defschema SliceProperties
  {:name s/Str
   :granularity s/Keyword
   :strategy s/Keyword
   (s/optional-key :filter) {s/Any s/Any}})

(s/defn slice-time :- java.util.Date
  [event-time :- java.util.Date
   granularity :- s/Keyword]
  (round-date event-time granularity))

(s/defn slice-time-filter :- {s/Any s/Any}
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
                   :year   "||+1Y")]

    {:range
     {:timestamp {:gte dt
                  :lt (str dt modifier)}}}))

(s/defn get-slice-props :- SliceProperties
  "make slice properties from a date an index name and config"
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
  (case (:strategy slice-props)
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
