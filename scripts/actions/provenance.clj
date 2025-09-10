#!/usr/bin/env bb

(ns actions.provenance
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]))

(defn -main [& args]
  (let [uber (io/file "target/ctia.jar")
        extract-dir "target/uberjar"
        {:keys [exit out err]} (when (.exists (io/file extract-dir))
                                 (sh/sh "rm" "-r" extract-dir))
        _ (when exit
            (assert (zero? exit) (str out "\n" err)))
        {:keys [exit out err]} (sh/sh "unzip" (.getPath uber) "-d" extract-dir)
        _ (assert (zero? exit) (str out "\n" err))
        ]
    (println "unzipped")
    ))

(when (= *file* (System/getProperty "babashka.file")) (-main))
