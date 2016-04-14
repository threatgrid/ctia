(ns ctia.lib.map)

(defn rmerge
  [& vals]
  (if (every? map? vals)
    (apply merge-with rmerge vals)
    (last vals)))
