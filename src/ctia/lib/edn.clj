(ns ctia.lib.edn
  (:refer-clojure :exclude [read-string])
  (:require [clojure.edn :as edn]))

(defn read-string
  "Try to read input string as edn. Return nil if failed."
  [^String st]
  (try
    (edn/read-string st)
    (catch Exception _)))
