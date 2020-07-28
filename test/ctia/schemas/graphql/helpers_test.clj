(ns ctia.schemas.graphql.helpers-test
  (:require [ctia.schemas.graphql.helpers :as sut]
            [ctia.graphql-service-core :refer [get-or-update-type-registry]]
            [clojure.test :as t :refer [deftest is testing]]))

(defn dummy-rt-opt []
  {:services
   {:GraphQLService
    {:get-or-update-type-registry
     (let [registry (atom {})]
       #(get-or-update-type-registry registry %1 %2))}}})

(deftest new-object-test
  (testing "The same object is not created twice"
    (let [rt-opt (dummy-rt-opt)]
      (is (= (-> (sut/new-object "Object"
                                 "Description 1"
                                 []
                                 {})
                 (sut/resolve-with-rt-opt rt-opt))
             (-> (sut/new-object "Object"
                                 "Description 2"
                                 []
                                 {})
                 (sut/resolve-with-rt-opt rt-opt)))))))

(deftest enum-test
  (testing "The same enum is not created twice"
    (let [rt-opt (dummy-rt-opt)]
      (is (= (-> (sut/enum "Enum"
                           "Description 1"
                           ["V1" "v2" "V3"])
                 (sut/resolve-with-rt-opt rt-opt))
             (-> (sut/enum "Enum"
                           "Description 2"
                           ["V1"])
                 (sut/resolve-with-rt-opt rt-opt)))))))

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
