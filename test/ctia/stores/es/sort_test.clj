(ns ctia.stores.es.sort-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [ctia.stores.es.sort :as sut]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :once
  validate-schemas)

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
         :asc))))
