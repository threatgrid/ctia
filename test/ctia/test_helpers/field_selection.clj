(ns ctia.test-helpers.field-selection
  (:require [clojure.string :as str]
            [clojure.test :refer [is testing]]
            [ctia.test-helpers.core :refer [GET]]))

(def default-fields
  #{:id
    :tlp
    :authorized_groups
    :authorized_users
    :owner
    :groups})

(defn testable-fields [fields]
  (remove default-fields fields))

(defn expected-field [field]
  (-> field
      name
      (str/split #"\.")
      first
      keyword))

(defn field-selection-test
  "all field selection related tests for given routes and fields"
  [app route headers fields]
  (testing (str "field selection tests for: " route)
    ;; TODO ensure non-empty
    (let [fields (testable-fields fields)]
      (assert (seq fields) route)
      (doseq [field fields]
        (let [{:keys [status parsed-body]}
              (GET app
                   route
                   :headers headers
                   :query-params {:fields [(name field)]})
              response-fields (if (vector? parsed-body)
                                (->> parsed-body
                                     (mapcat keys)
                                     set
                                     testable-fields)
                                (testable-fields (keys parsed-body)))]
          (is (= 200 status))
          (is (= [(expected-field field)] response-fields)))))))

(defn field-selection-tests [app routes headers fields]
  (assert (seq routes))
  (doseq [route routes]
    (field-selection-test app route headers fields)))
