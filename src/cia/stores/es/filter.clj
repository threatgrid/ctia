(ns cia.stores.es.filter
  (:require
   [clojure.string :as str]
   [clojurewerkz.elastisch.query :as q]))

(defn make-nested-query [[k v] filters]
  (map (fn [[k v]]
         (q/terms (->> k
                       (map name)
                       (str/join ".")) [v]))
       filters))

(defn mk-nested-filter [n-index]
  (map (fn [[path filters & _ :as group]]
         (q/nested
          :path (name path)
          :query (q/bool
                  {:must (make-nested-query group filters)}))) n-index))

(defn mk-flat-filter [flat-terms]
  (map (fn [[k v]] (q/terms k [v])) flat-terms))

(defn filter-map->terms-query [filter-map]
  "transforms a filter map to en ES terms query
   only supports one level of nesting"
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

(defn indicators-by-observable-query [judgement-ids]
  (let [f (q/nested
           :path "judgements"
           :query
           (q/bool {:must
                    {:terms
                     {:judgements.judgement judgement-ids}}}))]

    (q/filtered :filter f)))

(defn unexpired-judgements-by-observable-query
  [{:keys [value type]}]

  (let [observable-filter (q/nested :path "observable"
                                    :query
                                    (q/bool
                                     {:must [{:term {"observable.type" type}}
                                             {:term {"observable.value" value}}]}))
        time-ranges [{:range
                      {"valid_time.start_time" {"lt" "now/d"}}}
                     {:range
                      {"valid_time.end_time" {"gt" "now/d"}}}]

        time-filter (q/nested :path "valid_time"
                              :query
                              {:bool
                               {:must time-ranges}})]


    (q/filtered :query observable-filter
                :filter time-filter)))
