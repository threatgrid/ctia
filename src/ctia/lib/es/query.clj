(ns ctia.lib.es.query
  (:require [clojure.string :as str]
            [clojurewerkz.elastisch.query :as q]))

(defn nested-terms [filters]
  "make nested terms from a ctia filter:
  [[[:observable :type] ip] [[:observable :value] 42.42.42.1]]
  ->
  [{:terms {observable.type [ip]}} {:terms {observable.value [42.42.42.1]}}]

We we force all values to lowercase, since our indexing does the same for all terms.
"
  (vec (map (fn [[k v]]
              (q/terms (->> k
                            (map name)
                            (str/join "."))
                       (map str/lower-case
                            (if (coll? v) v [v]))))
            filters)))

(defn- relationship?
  "Check if some value correspond to a relationship query."
  [v]
  (and (set? v)
       (every? map? v)
       (every? #(= 1 (count (keys %))) v)))

(defn- terms-from-relationship
  "If we have a relationship kind of search

  {:related_COAs #{{:COA_id \"coa-1\"} {:COA_id \"coa-2\"}}}

  that will returns

  [[:related_COAs :COA_id] [\"coa-1\" \"coa-2\"]]
  "
  [t-key v]
  (let [common-key (first (keys (first v)))]
    [(concat t-key [common-key]) (mapv #(get % common-key) v)]))

(defn filter-map->terms-query
  "transforms a filter map to en ES terms query"
  ([filter-map]
   (filter-map->terms-query filter-map {:match_all {}}))
  ([filter-map query]
   (let [q (or query {:match_all {}})]
     (if filter-map
       (let [terms (map (fn [[k v]]
                          (let [t-key (if (sequential? k) k [k])]
                            (if (relationship? v)
                              (terms-from-relationship t-key v)
                              [t-key v])))
                        filter-map)]
         {:filtered
          {:query q
           :filter (q/bool {:must (nested-terms terms)})}})
       q))))
