(ns ctia.task.migration.store-test
  (:require [ctia.task.migration.store :as sut]
            [clojure.test :refer [deftest is testing]]))

(deftest prefixed-index-test
  (is (= "v0.4.2_ctia_actor"
         (sut/prefixed-index "ctia_actor" "0.4.2")))
  (is (= "v0.4.2_ctia_actor"
         (sut/prefixed-index "v0.4.1_ctia_actor" "0.4.2"))))

(deftest target-index-settings-test
  (is (= {:index {:number_of_replicas 0
                  :refresh_interval -1
                  :mapping {}}}
         (sut/target-index-settings {:mapping {}})))
  (is (= {:index {:number_of_replicas 0
                  :refresh_interval -1}}
         (sut/target-index-settings {}))))
