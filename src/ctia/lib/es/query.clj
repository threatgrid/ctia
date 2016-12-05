(ns ctia.lib.es.query
  (:require [clojure.string :as str]))

(defn bool
  "Boolean Query"
  [opts]
  {:bool opts})

(defn filtered
  "Filtered query"
  [opts]
  {:filtered opts})

(defn nested
  "Nested document query"
  [opts]
  {:nested opts})

(defn term
  "Term Query"
  ([key values] (term key values nil))
  ([key values opts]
   (merge { (if (coll? values) :terms :term) (hash-map key values) }
          opts)))

(defn terms
  "Terms Query"
  ([key values] (terms key values nil))
  ([key values opts]
   (term key values opts)))

(defn nested-terms [filters]
  "make nested terms from a ctia filter:
  [[[:observable :type] ip] [[:observable :value] 42.42.42.1]]
  ->
  [{:terms {observable.type [ip]}} {:terms {observable.value [42.42.42.1]}}]

we force all values to lowercase, since our indexing does the same for all terms."
  (vec (map (fn [[k v]]
              (terms (->> k
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
                        filter-map)
             must-filters (nested-terms terms)]

         (if (empty? must-filters)
           q {:filtered
              {:query q
               :filter (bool {:must must-filters})}}))

       q))))
