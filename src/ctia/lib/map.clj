(ns ctia.lib.map
  (:require [clojure.set :as set]))

(defn rmerge
  [& vals]
  (if (every? map? vals)
    (apply merge-with rmerge vals)
    (last vals)))

;; from http://stackoverflow.com/questions/21768802/how-can-i-get-the-nested-keys-of-a-map-in-clojure
(defn keys-in [m]
  (if (map? m)
    (vec
     (mapcat (fn [[k v]]
               (let [sub (keys-in v)
                     nested (map #(into [k] %) (remove empty? sub))]
                 (if (seq nested)
                   nested
                   [[k]])))
             m))
    []))

(defn keys-in-all [& ms]
  (apply set/intersection (->> ms
                               (map keys-in)
                               (map set))))
