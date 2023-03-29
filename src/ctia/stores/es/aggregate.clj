(ns ctia.stores.es.aggregate
  (:require ;[ctia.schemas.core :refer [AggregateExtension]]
            [clojure.string :as str]
            [schema.core :as s]))

(s/defn ^:private incident-aggregate-new-to-open-query [order
                                                        earlier-time
                                                        later-time]
  {:track_total_hits "true"
   ;:size 3
   :query {:bool {:filter [{:exists {:field (str "incident_time." earlier-time)}}
                           {:exists {:field (str "incident_time." later-time)}}
                           ;{:term {:status "Closed"}}
                           ]}}
   :sort {:_script {:type "number"
                    :script {:lang "painless"
                             :source (format "return (doc['incident_time.%s'].value.getMillis() - doc['incident_time.%s'].value.getMillis())"
                                             later-time earlier-time)}
                    :order (or order "desc")}}
   :aggs {:avg_timedifference {:avg {:script (format "return (doc['incident_time.%s'].value.getMillis() - doc['incident_time.%s'].value.getMillis()) / 1000 / 60 / 60 / 24"
                                                     later-time
                                                     earlier-time)}}}
   :_source ["incident_time" "status"]})

(s/defn parse-aggregate-op [op]
  (case op
    :ctia.entity.incident/aggregate-new-to-open (incident-aggregate-new-to-open-query nil #_"new" "discovered" "opened")
    :ctia.entity.incident/aggregate-open-to-closed (incident-aggregate-new-to-open-query nil "opened" "closed")))
