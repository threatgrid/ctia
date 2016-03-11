(ns cia.stores.es.query
  (:require
   [clojure.string :as str]
   [clojurewerkz.elastisch.query :as q]))

(defn nested-terms [filters]
  "make nested terms from a cia filter:
  [[[:observable :type] ip] [[:observable :value] 42.42.42.1]]
  ->
  [{:terms {observable.type [ip]}} {:terms {observable.value [42.42.42.1]}}]"
  (vec (map (fn [[k v]]
              (q/terms (->> k
                            (map name)
                            (str/join ".")) [v]))
            filters)))

(defn mk-nested-filter [n-index]
  "transformed a nested filter map grouped by parent path to an ES nested filter:
   {:observable [[[:observable :type] ip] [[:observable :value] 42.42.42.1]]}
   ->
   ({:nested {:path observable,
              :query {:bool
                       {:must ({:terms {observable.type [ip]}}
                               {:terms {observable.value [42.42.42.1]}})}}}})"
  (map (fn [[path filters]]
         (let [nested (nested-terms filters)]
           (q/nested
            :path (name path)
            :query (q/bool
                    {:must nested})))) n-index))

(defn mk-flat-filter [flat-terms]
  "transform simple filters to ES terms:
  {:judgement judgement-a71c737f-a3bc-428d-a644-1676e00a758d}
  ->
  ({:terms {:judgement [judgement-a71c737f-a3bc-428d-a644-1676e00a758d]}})"

  (vec (map (fn [[k v]] (q/terms k [v])) flat-terms)))

(defn filter-map->terms-query
  "transforms a filter map to en ES terms query
   only supports one level of nesting"

  [filter-map]

  (let [flat-terms (into {}
                         (filter #(keyword? (first %)) filter-map))
        nested-terms (into {}
                           (filter #(vector? (first %)) filter-map))
        n-index (group-by #(ffirst %) nested-terms)
        nested-fmt (mk-nested-filter n-index)
        flat-fmt (mk-flat-filter flat-terms)]

    {:filtered
     {:query {:match_all {}}
      :filter (q/bool {:must (concat nested-fmt
                                     flat-fmt)})}}))

(defn indicators-by-judgements-query
  "nested filter to get all indicators
   matching a set of judgement ids"
  [judgement-ids]

  (let [f (q/nested
           :path "judgements"
           :query
           (q/bool {:must
                    (q/terms :judgements.judgement_id
                             judgement-ids)}))]

    (q/filtered :filter f)))

(def unexpired-time-range
  "ES filter that matches objects which
  valid time range is not expired"

  [{:range
    {"valid_time.start_time" {"lt" "now/d"}}}
   {:range
    {"valid_time.end_time" {"gt" "now/d"}}}])

(defn unexpired-judgements-by-observable-query
  "a filtered query to get judgements for the specified
  observable, where valid time is in now range"

  [{:keys [value type]}]

  (let [observable-filter
        (q/nested :path "observable"
                  :query
                  (q/bool
                   {:must [{:term {"observable.type" type}}
                           {:term {"observable.value" value}}]}))

        time-filter
        (q/nested :path "valid_time"
                  :query
                  (q/bool {:must unexpired-time-range}))]

    (q/filtered :query observable-filter
                :filter time-filter)))
