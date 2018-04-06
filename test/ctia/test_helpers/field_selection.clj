(ns ctia.test-helpers.field-selection
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is testing]]
            [ctia.test-helpers [core :as helpers :refer [get]]]))

(def default-fields
  #{:id
    :tlp
    :authorized_groups
    :authorized_users})

(defn testable-fields [fields]
  (remove default-fields fields))

(defn expected-field [field]
  (-> field
      name
      (clojure.string/split #"\.")
      first
      keyword))

(defn field-selection-test
  "all field selection related tests for given routes and fields"
  [route headers fields]
  (testing (str "field selection tests for: " route)
    (doseq [field (testable-fields fields)]
      (let [{:keys [status parsed-body]}
            (get route
                 :headers headers
                 :query-params {:fields [(name field)]})
            response-fields (if (vector? parsed-body)
                              (->> parsed-body
                                   (mapcat keys)
                                   set
                                   testable-fields)
                              (testable-fields (keys parsed-body)))]
        (is (= 200 status))
        (is (= [(expected-field field)] response-fields))))))

(defn field-selection-tests [routes headers fields]
  (doseq [route routes]
    (field-selection-test route headers fields)))
