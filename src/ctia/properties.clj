(ns ctia.properties
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cprop.core :refer [load-config]]
            [cprop.source :refer [from-system-props
                                  from-env]])
  (:import java.util.Properties
           java.io.BufferedReader
           java.io.File))

(def property-files ["ctia.properties"
                     "ctia-default.properties"])

(defonce properties (atom {}))

(defn load
  "load a props file"
  [^BufferedReader reader]
  (doto (Properties.)
    (.load reader)))

(defn set-system-property! [sk v]
  "set one system property when not defined from cli"
  (let [sys-key (clojure.string/replace k #"\." "_")]
    (when-not (System/getProperty sys-key)
      (System/setProperty sys-key v))))

(defn set-system-properties!
  "set default system properties when not defined from cli"
  [file]

  (let [props (->> (io/reader file)
                   load)]

    (dorun (map (fn [[k v]]
                  (set-system-property! k v)) props))))

(defn try-read-file [file]
  "try read one prop file"
  (try
    (-> file io/resource io/reader)
    (catch Throwable e nil)))

(defn try-read-files [files]
  "try read all prop files"
  (map try-read-file files))

(defn init!
  "setup the properties atom,
  either by reading first avail prop file or supplying a file"
  ([]
   (some->> (try-read-files property-files)
            (filter some?)
            first
            set-system-properties!)
   (reset! properties
           (load-config :merge [(from-system-props)
                                (from-env)])))
  ([file]
   (some->> (try-read-file file)
            set-system-properties!)
   (reset! properties
           (load-config :merge [(from-system-props)
                                (from-env)]))))
