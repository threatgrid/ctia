(ns ctia.lib.es.schemas
  "All ES related schemas should be defined here"
  (:require [schema.core :as s])
  (:import [org.apache.http.impl.conn PoolingClientConnectionManager
            PoolingHttpClientConnectionManager]))

(s/defschema ESConn
  "an ES conn is a map with a
   connection manager and an index name"
  {:cm (s/either PoolingClientConnectionManager
                 PoolingHttpClientConnectionManager)
   :uri s/Str})

(s/defschema Refresh
  "ES refresh parameter, see
   https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-refresh.html"
  (s/enum true false "wait_for"))

(s/defschema ESSlicing
  {:strategy s/Keyword
   :granularity s/Keyword})

(s/defschema ESConnState
  "a Store ESConnState shall contain an ESConn
   and all store properties"
  {:index s/Str
   :props {s/Any s/Any}
   :config {s/Any s/Any}
   :conn ESConn
   (s/optional-key :slicing) ESSlicing})

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
