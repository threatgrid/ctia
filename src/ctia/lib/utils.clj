(ns ctia.lib.utils
  (:require [clojure.walk :as walk]))

(defn filter-out-creds [m]
  (reduce-kv (fn [acc k v]
               (if (re-matches #"(?i).*(key|pass|token|secret).*" (str k))
                 (assoc acc k "********")
                 (assoc acc k v)))
             m
             m))

(defn deep-filter-out-creds [m]
  (walk/prewalk #(if (map? %)
                   (filter-out-creds %)
                   %)
                m))

(defn safe-pprint [& xs]
  (->> xs
       (map deep-filter-out-creds)
       (apply clojure.pprint/pprint)))
