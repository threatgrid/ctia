#! /usr/bin/env clojure

(ns summarize-tests
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp])
  (:import [java.io File]))

(defn summarize []
  (println
    "Test summary:\n"
    (with-out-str
      (pp/pprint
        (->> (file-seq (io/file "target/test-results"))
             (filter (fn [^File f]
                       (and (.isFile f)
                            (.startsWith (.getName f)
                                         "ns-timing"))))
             (map (comp read-string slurp))
             (apply merge))))))

(summarize)
