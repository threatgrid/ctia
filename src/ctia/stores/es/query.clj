(ns ctia.stores.es.query
  (:require [clojure.string :as str]
            [clojurewerkz.elastisch.query :as q]))

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


(defn sightings-by-observables-query
  "nested filter to get all indicators
   matching a set of judgement ids"
  [observables]

  (q/nested
   :path "observables"
   :query (q/bool {:should
                   (map (fn [{:keys [type value]}]
                          (q/bool
                           {:must
                            [{:term {"observables.type" type}}
                             {:term {"observables.value" value}}]})) observables)})))
