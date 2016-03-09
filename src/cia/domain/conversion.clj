(ns cia.domain.conversion)

(defn ->confidence [num]
  (cond
    (nil? num)  "Unknown"
    (= 0 num)   "None"
    (>= num 95) "High"
    (>= num 85) "Medium"
    :else       "Low"))
