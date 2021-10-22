(ns ctia.schemas.graphql.flanders-test
  (:require [ctia.schemas.graphql.flanders :as sut]
            [clojure.test :as t :refer [is deftest]]))

(deftest conditional-type-resolver-test
  (let [type-resolver (sut/conditional-type-resolver
                       [#(= (:type %) "judgement")
                        #(= (:type %) "sighting")]
                       ["Judgement"
                        "Sighting"])]
    (is (= "Judgement"
           (type-resolver {:type "judgement"} nil nil)))
    (is (= "Sighting"
           (type-resolver {:type "sighting"} nil nil)))
    (is (nil? (type-resolver {:type "feedback"} nil nil)))))

