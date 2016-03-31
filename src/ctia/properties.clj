(ns ctia.properties
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ctia.lib.map :as map])
  (:import java.util.Properties))

(def property-files ["ctia.properties"
                     "ctia-default.properties"])

(defonce properties (atom {}))

(defn- read-property-file []
  (->> property-files
       (keep (fn [file]
               (when-let [rdr (some-> file io/resource io/reader)]
                 (with-open [rdr rdr]
                   (doto (Properties.)
                     (.load rdr))))))
       first
       (into {})))

(defn- transform [properties]
  (reduce (fn [accum [k v]]
            (let [parts (->> (str/split k #"\.")
                             (map keyword))]
              parts
              (cond
                (empty? parts) accum
                (= 1 (count parts)) (assoc accum (first parts) v)
                :else (map/rmerge accum
                                  (assoc-in {} parts v)))))
          {}
          properties))

(defn init! []
  (reset! properties
          (transform
           (let [properties-from-file (read-property-file)]
             (merge properties-from-file
                    (select-keys (System/getProperties)
                                 (keys properties-from-file)))))))
