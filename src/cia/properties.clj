(ns cia.properties
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import java.util.Properties))

(def properties-file-name "cia.properties")
(def default-properties-file-name "cia-default.properties")

(defonce properties (atom nil))
(defonce default-properties (atom nil))

(defn load [file-name]
  (try
    (doto (Properties.)
      (.load (-> file-name
                 io/resource
                 io/reader)))
    (catch Throwable e
      (println (format "Could not load '%s' properties file" file-name)))))

(defn transform [properties]
  (reduce (fn [accum [k v]]
            (let [parts (str/split k #"\.")]
              (cond
                (empty? parts) accum
                (= 1 (count parts)) (assoc accum (keyword k) v)
                :else (assoc-in accum (map keyword parts) v))))
          {}
          properties))

(defn init! []
  (reset! properties (transform (load properties-file-name)))
  (reset! default-properties (transform (load default-properties-file-name))))

(defn prop [& key-path]
  (->> (map #(get-in % key-path ::miss) [@properties @default-properties])
       (filter #(not= % ::miss))
       first))
