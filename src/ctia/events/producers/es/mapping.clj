(ns ctia.events.producers.es.mapping)

(def string {:type "string" :index "not_analyzed"})

(def producer-mappings
  {"event"
   {:properties
    {:owner string
     :model {:type "object"}
     :id string
     :http-params {:type "object"}
     :type string
     :fields {:type "object"}
     :judgement_id string
     :verdict {:type "object"}}}})
