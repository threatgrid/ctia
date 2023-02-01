(ns ctia.stores.es.search
  (:require [ctia.schemas.core :refer [SearchExtension]]
            [clojure.string :as str]
            [schema.core :as s]))

(s/defn parse-search-params-op
  [{:keys [op field-name ext-val] :as params} :- ConcreteSearchExtension]
  (case op
    :filter-list-range (let [{:keys [comparator-kw base-list-field nested-range-field nested-elem-filter]} params
                             es-comparator (case comparator-kw
                                             :from "gte"
                                             :to "lte")
                             field-name (keyword (str outer-field-name "." inner-field-name))]
                         (assert (number? ext-val))
                         {:query
                          {:nested
                           {:path base-list-field
                            :query {:bool
                                    {:filter
                                     (cond-> [{:range {(str base-list-field "." nested-range-field) {es-comparator ext-val}}}]
                                       nested-elem-filter (conj {:match (update-keys filter-entry #(str base-list-field "." %))}))}}}}})))
