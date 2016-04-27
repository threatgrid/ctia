(ns ctia.lib.map)

(defn rmerge
  [& vals]
  (if (every? map? vals)
    (apply merge-with rmerge vals)
    (last vals)))

(defn dissoc-in
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (assoc m k newmap))
      m)
    (dissoc m k)))
