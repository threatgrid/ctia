(ns ctia.stores.es.sort
  (:require [ctia.schemas.core :refer [ConcreteSortExtension]]
            [clojure.string :as str]
            [schema.core :as s]))

(s/defn parse-sort-params-op
  [{:keys [op field-name sort_order] :as params} :- ConcreteSortExtension
   default-sort_order :- (s/cond-pre s/Str s/Keyword)]
  (let [field-name (name field-name)
        order (keyword (or sort_order default-sort_order))]
    (assert (not (some #{"'"} field-name)) (pr-str field-name))
    (case op
      ;; eg
      ;; {:op :field
      ;;  :field-name "Severity"
      ;;  :sort_order :asc}
      :field
      ;; https://www.elastic.co/guide/en/elasticsearch/reference/current/sort-search-results.html#_sort_values
      {field-name {:order order}}

      ;; eg
      ;; {:op :remap
      ;;  :field-name "severity"
      ;;  :remappings {"Critical" 0
      ;;               "High" 1}
      ;;  :sort_order :asc
      ;;  :remap-default 0}
      :remap
      (let [{:keys [remap-default remappings]} params
            remappings (into {} (map (fn [e]
                                       (update e 0 #(cond-> %
                                                      (string? %) str/lower-case))))
                             remappings)]
        ;; https://www.elastic.co/guide/en/elasticsearch/painless/current/painless-sort-context.html
        {:_script
         {:type "number"
          :script {:lang "painless"
                   :inline (str/join
                             "\n"
                             ;; https://www.elastic.co/guide/en/elasticsearch/painless/8.1/painless-walkthrough.html#_missing_keys
                             [(format "if (!doc.containsKey('%s') || doc['%s'].size() != 1) { return params.default }"
                                      field-name field-name)
                              (format "return params.remappings.getOrDefault(doc['%s'].value, params.default)"
                                      field-name)])
                   :params {:remappings remappings
                            :default remap-default}}
          :order order}}))))
