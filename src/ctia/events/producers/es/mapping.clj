(ns ctia.events.producers.es.mapping)

(def ts {:type "date" :format "date_time"})
(def string {:type "string" :index "not_analyzed"})

(def dynamic-templates
  [{:date_as_datetime {:match "*"
                       :match_mapping_type "date"
                       :mapping ts}}
   {:string_not_analyzed {:match "*"
                          :match_mapping_type "string"
                          :mapping string}}])

(def producer-mappings
  {"event"
   {:dynamic_templates dynamic-templates
    :properties
    {:owner string
     :timestamp ts
     :model {:type "object"}
     :id string
     :http-params {:type "object"}
     :type string
     :fields {:type "object"}
     :judgement_id string
     :verdict {:type "object"}}}})
