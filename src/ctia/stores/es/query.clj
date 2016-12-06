(ns ctia.stores.es.query
  (:require
   [clojure.string :as str]
   [ctia.lib.es.query :as q]))

(def unexpired-time-range
  "ES filter that matches objects which
  valid time range is not expired"

  [{:range
    {"valid_time.start_time" {"lt" "now/d"}}}
   {:range
    {"valid_time.end_time" {"gt" "now/d"}}}])

(defn active-judgements-by-observable-query
  "a filtered query to get judgements for the specified
  observable, where valid time is in now range"
  [{:keys [value type]}]

  (q/bool {:filter (concat
                    unexpired-time-range
                    [{:term {"observable.type" type}}
                     {:term {"observable.value" value}}])}))

(defn nested-terms [filters]
  "make nested terms from a ctia filter:
  [[[:observable :type] ip] [[:observable :value] 42.42.42.1]]
  ->
  [{:terms {observable.type [ip]}} {:terms {observable.value [42.42.42.1]}}]

we force all values to lowercase, since our indexing does the same for all terms."
  (vec (map (fn [[k v]]
              (q/terms (->> k
                            (map name)
                            (str/join "."))
                       (map str/lower-case
                            (if (coll? v) v [v]))))
            filters)))

(defn filter-map->terms-query
  "transforms a filter map to en ES terms query"
  ([filter-map]
   (filter-map->terms-query filter-map {:match_all {}}))
  ([filter-map query]
   (let [q (or query {:match_all {}})]
     (if filter-map
       (let [terms (map (fn [[k v]]
                          (let [t-key (if (sequential? k) k [k])]
                            [t-key v]))
                        filter-map)
             must-filters (nested-terms terms)]

         (if (empty? must-filters)
           q {:bool
              {:must q
               :filter (q/bool {:must must-filters})}}))
       q))))
