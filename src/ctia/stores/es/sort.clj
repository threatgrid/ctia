(ns ctia.stores.es.sort
  (:require [ctia.schemas.core :refer [SortExtension]]
            [clojure.string :as str]
            [schema.core :as s]))

;; https://www.elastic.co/guide/en/elasticsearch/painless/8.1/painless-walkthrough.html#_missing_keys
(defn- painless-return-default-if-no-field [field-name default-param]
  (format "if (!doc.containsKey('%s') || doc['%s'].size() != 1) { return params.%s }"
          field-name field-name default-param))

(defn- normalize-remappings [remappings]
  (update-keys remappings #(cond-> %
                             (string? %) str/lower-case)))

(s/defn parse-sort-params-op
  "Supported operations:

  Sort by field value:
      eg., sort on Severity, ascending:
      {:op :field
       :field-name \"Severity\"
       :sort_order :asc}

  Remap field value, then sort on remapping:
      eg., remap severity to to integers, then sort ints:
      {:op :remap
       :field-name \"severity\"
       :remappings {\"Critical\" 0
                    \"High\" 1}
       :sort_order :asc
       :remap-default 0}

  Remap field value (a list) to the maximum int in remappings, then sort on that:
      eg. find maximum remapping of tactics (a list of strings) then sort ascending.
      {:op :remap-list-max
       :field-name \"tactics\"
       :remappings {\"TA0043\" 1
                    \"TA0042\" 2}
       :sort_order :asc}"
  [{:keys [op field-name sort_order] :as params} :- SortExtension
   default-sort_order :- (s/cond-pre s/Str s/Keyword)]
  (let [field-name (name field-name)
        order (keyword (or sort_order default-sort_order))]
    (assert (not (some #{"'"} field-name)) (pr-str field-name))
    (case op
      :field
      ;; https://www.elastic.co/guide/en/elasticsearch/reference/current/sort-search-results.html#_sort_values
      {field-name {:order order}}

      (:remap :remap-list-max)
      (let [{:keys [remap-default remappings]} params
            remappings (normalize-remappings remappings)]
        ;; https://www.elastic.co/guide/en/elasticsearch/painless/current/painless-sort-context.html
        {:_script
         {:type "number"
          :script {:lang "painless"
                   :inline (str/join "\n"
                                     (case op
                                       :remap ["if (!doc.containsKey(params.fieldName) || doc[params.fieldName].size() != 1) { return params.default }"
                                               "return params.remappings.getOrDefault(doc[params.fieldName].value, params.default)"]
                                       :remap-list-max ["if (!doc.containsKey(params.fieldName)) { return params.default }"
                                                        "double score = params.default;"
                                                        "for (int i = 0; i < doc[params.fieldName].length; ++i) {"
                                                        "  score = Math.max(score, params.remappings.getOrDefault(doc[params.fieldName][i], params.default));"
                                                        "}"
                                                        "return score;"]))
                   :params {:remappings remappings
                            :fieldName field-name
                            :default remap-default}}
          :order order}}))))
