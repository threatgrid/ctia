(ns ctia.schemas.search-agg
  (:require [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema DateRange
  "Date range query, includes lowerfrom and excludes to"
  {s/Keyword
   (st/optional-keys
    {:gte (s/conditional
            string? s/Str
            :else s/Inst)
     :lt (s/conditional
           string? s/Str
           :else s/Inst)})})

(s/defschema SearchQuery
  "components of a search query:
   - query-string: free text search, with lucene syntax enabled"
  (st/optional-keys
   {:query-string s/Str
    :filter-map {(s/conditional
                   string? s/Str
                   :else s/Keyword)
                 s/Any}
    :date-range DateRange}))

(s/defschema AggType
  "supported aggregation types"
  (s/enum :histogram :topn :cardinality))

(s/defschema AggCommonParams
  (st/merge
   {:aggregate-on s/Str}))

(s/defschema Timezone
  (let [positives (map #(format "+%02d:00" %) (range 12))
        negatives (map #(format "-%02d:00" %) (range 1 12))]
    (->> (concat positives negatives)
         (apply s/enum))))

(s/defschema HistogramParams
  (st/merge
   AggCommonParams
   {:granularity (s/enum :day :week :month)
    (s/optional-key :timezone) Timezone}))

(s/defschema HistogramQuery
  (st/merge
   {:agg-type (s/eq :histogram)}
   HistogramParams))

(s/defschema TopnParams
  (st/merge
   AggCommonParams
   (st/optional-keys
    {:limit s/Int
     :sort_order (s/enum :asc :desc)})))

(s/defschema TopnQuery
  (st/merge
   {:agg-type (s/eq :topn)}
   TopnParams))

(s/defschema CardinalityParams AggCommonParams)

(s/defschema CardinalityQuery
  (st/merge
   {:agg-type (s/eq :cardinality)}
   AggCommonParams))

(s/defschema AggQuery
  (st/open-schema
   {:agg-type AggType
    :aggregate-on s/Str}))

(s/defschema MetricResult
  {:data {s/Keyword s/Any}
   :type AggType
   :filters (st/open-schema
             {:from s/Inst
              :to s/Inst})})
