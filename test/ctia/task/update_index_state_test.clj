(ns ctia.task.update-index-state-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [ctia.task.update-index-state :as sut]
            [ductile.index :as es-index]
            [ctia.test-helpers.core :as h]
            [ctia.test-helpers.es :as es-helpers]))

(use-fixtures :once
  es-helpers/fixture-properties:es-store)

(deftest update-index-state-test
  (es-helpers/for-each-es-version
    "update-index-state task should succeed"
    [5 7]
    #(ductile.index/delete! % "ctia_*")
    (es-helpers/fixture-properties:es-store
      (fn []
        (is (zero? (sut/do-task (h/build-transformed-init-config))))))))
