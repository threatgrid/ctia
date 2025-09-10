#!/usr/bin/env bb

(ns actions.provenance
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]))

(def uber-path "target/ctia.jar")
(def extract-dir "target/uberjar")

(defn from-this-repo? [fstr]
  (let [src-f (io/file (str "src/" fstr))]
    (and (.exists src-f)
         (= (slurp (str extract-dir "/" fstr))
            (slurp src-f)))))

(defn leiningen-project-file? [fstr]
  (and (str/starts-with? fstr "META-INF/leiningen")
       (str/ends-with? fstr "/project.clj")))

(defn unknown-clojure-files []
  (into [] (comp (filter #(str/ends-with? (.getPath (io/file %)) ".clj") )
                 (map #(subs (.getPath (io/file %)) (inc (count extract-dir))))
                 (remove from-this-repo?)
                 (remove leiningen-project-file?))
        (file-seq (io/file extract-dir))))

(defn -main [& args]
  (let [uber (io/file uber-path)
        {:keys [exit out err]} (when (.exists (io/file extract-dir))
                                 (sh/sh "rm" "-r" extract-dir))
        _ (when exit
            (assert (zero? exit) (str out "\n" err)))
        {:keys [exit out err]} (sh/sh "unzip" (.getPath uber) "-d" extract-dir)
        _ (assert (zero? exit) (str out "\n" err))]
    (println "unzipped")
    (if-some [unknown (not-empty (unknown-clojure-files))]
      (do (prn unknown)
          (println "Unknown Clojure files found.")
          (System/exit 1))
      (System/exit 0))))

(when (= *file* (System/getProperty "babashka.file")) (-main))
