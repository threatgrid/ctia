(ns ctia.stores.es.query
  (:require
   [clojure.string :as str]
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
                     (if (vector? k)
                       [k v]
                       [[k] v])) filter-map)]
    {:filtered
     {:query {:match_all {}}
      :filter (q/bool {:must (nested-terms terms)})}}))

(defn indicators-by-judgements-query
  "filter to get all indicators
   matching a set of judgement ids"
  [judgement-ids]

  (let [f (q/bool
           {:must (q/terms :judgements.judgement_id
                           judgement-ids)})]
    (q/filtered
     :query {:match_all {}}
     :filter f)))

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

  (let [observable-filter
        (q/bool
         {:must [{:term {"observable.type" type}}
                 {:term {"observable.value" value}}]})
        time-filter (q/bool {:must unexpired-time-range})]

    (q/filtered :query observable-filter
                :filter time-filter)))
