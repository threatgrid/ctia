(ns ctia.schemas.utils-test
  (:require [clojure.test :refer [deftest is testing]]
            [ctia.schemas
             [core :refer [CTIMNewBundle]]
             [utils :as sut]]
            [schema-tools.walk :refer [walk]]))

(defn collect-schema-version-leaves
  [collector m]
  (walk
   (fn [x]
     (if (and
          (instance? clojure.lang.IMapEntry x)
          (= :schema_version (:k (first x))))
       (do (swap! collector conj (.getName (last x))) x)
       (collect-schema-version-leaves collector x)))
   identity
   m))

(deftest recursvie-open-schema-version-test
  (testing "should replace all inners for schema_version with s/Str"
    (let [res-schema (sut/recursive-open-schema-version CTIMNewBundle)
          found-leaves (atom [])]
      (collect-schema-version-leaves found-leaves res-schema)
      (is (seq @found-leaves))
      (is (every? #(= "java.lang.String" %) @found-leaves)))))
