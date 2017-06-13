(ns ctia.schemas.graphql.sorting-test
  (:require [ctia.schemas.graphql.sorting :as sut]
            [clojure.test :as t :refer [deftest is testing]]))

(deftest connection-params->sorting-params-test
  (testing "with one field"
    (is (= {:sort_by "field1:ASC"}
           (sut/connection-params->sorting-params
            {:orderBy [{:field "field1"
                        :direction "ASC"}]}))))
  (testing "with multiple fields"
    (is (= {:sort_by "field1:ASC,field2:DESC"}
           (sut/connection-params->sorting-params
            {:orderBy [{:field "field1"
                        :direction "ASC"}
                       {:field "field2"
                        :direction "DESC"}]})))))

(deftest sorting-kw->enum-name
  (is (= "VALID_TIME_START_TIME"
         (sut/sorting-kw->enum-name
          :valid_time.start_time))))
