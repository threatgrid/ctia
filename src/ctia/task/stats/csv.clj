(ns ctia.task.stats.csv
  (:require [clojure.string :as string]))

(defn ^:private field-protect
  [txt]
  (str "\""
       (-> (cond (string? txt) txt
                 (keyword? txt) (name txt)
                 :else (pr-str txt))
           (string/replace #"\"" "'")
           (string/replace #"\n" " "))
       "\""))

(defn- deep-flatten-map-as-couples
  [prefix m]
  (apply concat
         (for [[k v] m]
           (let [k-str (if (keyword? k) (name k) (str k))
                 new-pref (if (empty? prefix)
                            k-str
                            (str (name prefix) " " k-str))]
             (cond
               (map? v) (deep-flatten-map-as-couples new-pref v)
               (and (coll? v)
                    (first v)
                    (map? (first v)))
               (apply concat
                      (map-indexed #(deep-flatten-map-as-couples
                                     (if (> %1 0)
                                       (str new-pref "-" %1)
                                       new-pref)
                                     %2)
                                   v))
               :else [[(name new-pref)
                       (if (string? v) v (pr-str v))]])))))

(defn deep-flatten-map
  ([m] (deep-flatten-map "" m))
  ([prefix m]
   (into {} (deep-flatten-map-as-couples prefix m))))


(defn to-csv
  "Take a list of hash-map and output a CSV string
  Take it wasn't optimized and I might consume a lot of memory."
  ^String
  [ms]
  (let [flattened-ms (map deep-flatten-map ms)
        ks (keys (apply merge flattened-ms))]
    (string/join "\n"
                 (cons (string/join "," (map #(field-protect (name %)) ks))
                       (for [m flattened-ms]
                         (string/join "," (map #(field-protect (get m % "")) ks)))))))
