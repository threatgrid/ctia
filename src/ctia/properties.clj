(ns ctia.properties
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cprop.source :refer [from-system-props
                                  from-env]]
            [cprop.core :refer [load-config]])

  (:import java.util.Properties
           java.io.BufferedReader
           java.io.File))

(def property-files ["ctia.properties"
                     "ctia-default.properties"])

(defonce properties (atom {}))

(defn load [^BufferedReader reader]
  (doto (Properties.)
    (.load reader)))

(defn set-system-properties! [file]

  (let [props (->> (io/reader file)
                   load)]

    (dorun (map (fn [[k v]]
                  (let [sk (clojure.string/replace k #"\." "_")]
                    (System/setProperty sk v))) props))))

(defn try-read-file [file]
  (try
    (-> file io/resource io/reader)
    (catch Throwable e nil)))

(defn try-read-files [files]
  (map try-read-file files))

(defn init!
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
