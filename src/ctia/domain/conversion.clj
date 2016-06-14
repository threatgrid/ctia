(ns ctia.domain.conversion)

(defn ->confidence [num]
  (cond
    (nil? num)  "Unknown"
    (zero? num) "None"
    (>= num 95) "High"
    (>= num 85) "Medium"
    :else       "Low"))
