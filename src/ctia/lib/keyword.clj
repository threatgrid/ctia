(ns ctia.lib.keyword
  (:require [clojure.string :as str]))

(defn singular
  "remove the last s of a keyword see test for an example."
  [k]
  (-> k
      name
      (str/replace #"s$" "")
      keyword))
