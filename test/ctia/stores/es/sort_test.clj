(ns ctia.stores.es.sort-test
  (:require [clojure.test :refer [deftest is]]
            [ctia.stores.es.sort :as sut]))

(deftest parse-sort-params-op-test
  (is (=
       {"Severity" {:order :asc}}
       (sut/parse-sort-params-op
         {:op :field
          :field-name "Severity"
          :sort_order :asc}
         :asc)))
  (is (=
       {:_script {:type "number"
                  :script {:lang "painless"
                           :inline (str "if (!doc.containsKey(params.fieldName) || doc[params.fieldName].size() != 1) { return params.default }\n"
                                        "return params.remappings.getOrDefault(doc[params.fieldName].value, params.default)")
                           :params {;; note: lowercased
                                    :remappings {"critical" 0, "high" 1}
                                    :fieldName "Severity"
                                    :default 0}}
                  :order :asc}}
       (sut/parse-sort-params-op
         {:op :remap
          :field-name "Severity"
          ;; note: keys are lowercased in script
          :remappings {"Critical" 0
                       "High" 1}
          :sort_order :asc
          :remap-default 0}
         :asc)))
  (is (= {"scores.score"
          {:order :asc
           :mode "max"
           :nested {:path "scores"
                    :filter {:term {"type" "asset"}}}}}
         (sut/parse-sort-params-op
           {:op :sort-by-list-max
            :field-name "scores"
            :max-entry "score"
            :filter-entry {"type" "asset"}
            :sort_order :asc}
           :asc))))
