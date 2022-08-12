(ns ctia.stores.es.sort
  (:require [ctia.schemas.core :refer [ConcreteSortExtension]]
            [clojure.string :as str]
            [schema.core :as s]))

;; https://www.elastic.co/guide/en/elasticsearch/painless/8.1/painless-walkthrough.html#_missing_keys
(defn- painless-return-default-if-no-field [field-name default-param]
  (format "if (!doc.containsKey('%s') || doc['%s'].size() != 1) { return params.%s }"
          field-name field-name default-param))

(defn- normalize-remappings [remappings]
  (into {} (map (fn [e]
                  (update e 0 #(cond-> %
                                 (string? %) str/lower-case))))
        remappings))

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

      ;; chooses the maximum value in field (a list) based on remappings,
      ;; and sorts based on that value for each entity. Remappings must
      ;; be positive integers.
      ;; eg
      ;; {:op :remap-list-max
      ;;  :field-name "tactics"
      ;;  :remappings {"TA0043" 1
      ;;               "TA0042" 2}
      ;;  :sort_order :asc}
      :remap-list-max
      (let [{:keys [remappings]} params
            remappings (normalize-remappings remappings)]
        (assert (every? (complement neg?) (vals remappings)) ":remap-list-max :remappings values must be non-negative")
        {:_script
         {:type "number"
          :script {:lang "painless"
                   :inline (str/join
                             "\n"
                             ["if (!doc.containsKey(params.fieldName)) { return params.default }"
                              "int score = params.default;"
                              "for (int i = 0; i < doc[params.fieldName].length; ++i) {"
                              "  score = Math.max(score, params.remappings.getOrDefault(doc[params.fieldName][i], params.default));"
                              "}"
                              "return score;"])
                   :params {:remappings remappings
                            :fieldName field-name
                            :default 0}}
          :order order}})

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
                             ["if (!doc.containsKey(params.fieldName) || doc[params.fieldName].size() != 1) { return params.default }"
                              "return params.remappings.getOrDefault(doc[params.fieldName].value, params.default)"])
                   :params {:remappings remappings
                            :fieldName field-name
                            :default remap-default}}
          :order order}}))))
