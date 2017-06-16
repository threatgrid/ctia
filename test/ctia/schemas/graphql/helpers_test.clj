(ns ctia.schemas.graphql.helpers-test
  (:require [ctia.schemas.graphql.helpers :as sut]
            [clojure.test :as t :refer [deftest is testing]]))

(deftest new-object-test
  (testing "The same object is not created twice"
    (is (= (sut/new-object "Object"
                           "Description 1"
                           []
                           {})
           (sut/new-object "Object"
                           "Description 2"
                           []
                           {})))))

(deftest enum-test
  (testing "The same enum is not created twice"
    (is (= (sut/enum "Enum"
                     "Description 1"
                     ["V1" "v2" "V3"])
           (sut/enum "Enum"
                     "Description 2"
                     ["V1"])))))

(deftest valid-type-name?-test
  (is (not (sut/valid-type-name? nil))
      "null type name is invalid")
  (is (not (sut/valid-type-name? ""))
      "empty name is invalid")
  (is (not (sut/valid-type-name? "a-b"))
      "name with dash char is invalid")
  (is (sut/valid-type-name? "judgement")
      "normal name is valid"))

(deftest valid-type-names?-test
  (is (not (sut/valid-type-names? ["a-b" "aa"]))
      "Invalid if one of the collection is not valid")
  (is (sut/valid-type-names? ["judgement" "indicator"])
      "Valid if all names are valid"))
