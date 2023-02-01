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
                            {:nested
                             {:path base-list-field
                              :query {:bool
                                      {:filter
                                       (cond-> [{:range {(str base-list-field "." nested-range-field) {es-comparator ext-val}}}]
                                         nested-elem-filter (conj {:match (update-keys nested-elem-filter #(str base-list-field "." %))}))}}}})))
