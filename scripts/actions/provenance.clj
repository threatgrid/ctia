#!/usr/bin/env bb

(ns actions.provenance
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]))

(defn -main [& args]
  (let [uber (io/file "target/ctia.jar")
        {:keys [exit out err]} (sh/sh "unzip" (.getPath uber))]

    (assert (zero? exit) (str out "\n" err))
    (println "unzipped")
    ))

(when (= *file* (System/getProperty "babashka.file")) (-main))
