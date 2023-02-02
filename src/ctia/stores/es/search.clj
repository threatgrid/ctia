(ns ctia.stores.es.search
  (:require [ctia.schemas.core :refer [ConcreteSearchExtension]]
            [clojure.string :as str]
            [schema.core :as s]))

(s/defn parse-search-params-op
  [{:keys [op ext-val] :as params} :- ConcreteSearchExtension]
  (case op
    :filter-by-list-range (let [{:keys [comparator-kw base-list-field nested-range-field nested-elem-filter]} params
                                es-comparator (case comparator-kw
                                                :from "gte"
                                                :to "lte")]
                            (assert (number? ext-val))
                            ;; https://www.elastic.co/guide/en/elasticsearch/reference/7.17/query-dsl-nested-query.html#query-dsl-nested-query
                            {:nested
                             {:path base-list-field
                              ;; higher range is more relevant
                              :score-mode "max"
                              :query {:bool
                                      {:filter
                                       (cond-> [{:range {(str base-list-field "." nested-range-field) {es-comparator ext-val}}}]
                                         ;; use :term instead of :match for exact match
                                         nested-elem-filter (conj {:term (update-keys nested-elem-filter #(str base-list-field "." %))}))}}}})))
