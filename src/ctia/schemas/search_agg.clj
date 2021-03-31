(ns ctia.schemas.search-agg
  (:require
   [schema.core :as s]
   [schema-tools.core :as st]))

(s/defschema RangeQueryOpt
  (st/optional-keys
    {:gte s/Inst
     :lt s/Inst}))

(s/defschema RangeQuery
  "Corresponds to Range Query of Elasticsearch Query DSL.
  see: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-range-query.html"
  {s/Keyword RangeQueryOpt})

(s/defschema SearchQuery
  "components of a search query:
   - query-string: free text search, with lucene syntax enabled"
  (st/optional-keys
   {:query-string s/Str         ;; TODO change as described: https://github.com/threatgrid/iroh/issues/4959#issuecomment-810815287
    :filter-map {s/Keyword s/Any}
    :range RangeQuery}))

(s/defschema AggType
  "supported aggregation types"
  (s/enum :histogram :topn :cardinality))

(s/defschema AggCommonParams
  {:aggregate-on s/Str
   (s/optional-key :agg-key) s/Keyword})

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
