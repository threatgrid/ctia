(ns ctia.test-helpers.collections
  (:require [clojure.test :refer [is]]))

(defn is-submap? [a b]
  (let [selected (select-keys b (keys a))]
    (is (= a selected))))
