(ns ctia.lib.es.query
  (:require [clojure.string :as str]
            [clojurewerkz.elastisch.query :as q]))

(defn nested-terms [filters]
  "make nested terms from a ctia filter:
  [[[:observable :type] ip] [[:observable :value] 42.42.42.1]]
  ->
  [{:terms {observable.type [ip]}} {:terms {observable.value [42.42.42.1]}}]"
  (vec (map (fn [[k v]]
              (q/terms (->> k
                            (map name)
                            (str/join "."))
                       (if (vector? v) v [v])))
            filters)))

(defn filter-map->terms-query
  "transforms a filter map to en ES terms query"
  [filter-map]

  (let [terms (map (fn [[k v]]
                     (if (sequential? k)
                       [k v]
                       [[k] v])) filter-map)]
    {:filtered
     {:query {:match_all {}}
      :filter (q/bool {:must (nested-terms terms)})}}))
