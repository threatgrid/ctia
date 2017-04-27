(ns ctia.stores.es.query
  (:require [clj-momo.lib.es.query :as q]
            [clojure.string :as str]))

(defn- unexpired-time-range
  "ES filter that matches objects which
  valid time range is not expired"
  [time-str]
  [{:range
    {"valid_time.start_time" {"lte" time-str}}}
   {:range
    {"valid_time.end_time" {"gt" time-str}}}])

(defn active-judgements-by-observable-query
  "a filtered query to get judgements for the specified
  observable, where valid time is in now range"
  [{:keys [value type]} time-str]

  (concat
   (unexpired-time-range time-str)
   [{:term {"observable.type" type}}
    {:term {"observable.value" value}}]))

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

;; TODO figure out better that part
(defn filter-map->terms-query
  "transforms a filter map to en ES terms query"
  ([filter-map]
   (filter-map->terms-query filter-map nil))
  ([filter-map query]

   (let [terms (map (fn [[k v]]
                      (let [t-key (if (sequential? k) k [k])]
                        [t-key v]))
                    filter-map)
         must-filters (nested-terms terms)]

     (cond
       ;; a filter map and a query
       (and filter-map query)
       {:bool
        {:filter (conj must-filters query)}}

       ;; only a filter map
       (and filter-map (nil? query))
       {:bool
        {:filter must-filters}}

       ;; a query without a filter map
       (and (empty? filter-map) query)
       {:bool
        {:filter query}}

       ;; if we neither have a filter map or a query
       :else
       {:bool
        {:match_all {}}}))))
