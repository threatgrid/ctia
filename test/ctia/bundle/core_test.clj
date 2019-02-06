(ns ctia.bundle.core-test
  (:require [ctia.bundle.core :as sut]
            [clojure.test :as t :refer [deftest testing use-fixtures are is]]
            [ctia.test-helpers.core :as h]))

(use-fixtures :once h/fixture-properties:clean)

(deftest local-entity?-test
  (are [x y] (= x (sut/local-entity? y))
    false nil
    false "http://unknown.site/ctia/indicator/indicator-56067199-47c0-4294-8957-13d6b265bdc4"
    true "indicator-56067199-47c0-4294-8957-13d6b265bdc4"
    true "http://localhost:57254/ctia/indicator/indicator-56067199-47c0-4294-8957-13d6b265bdc4"))

