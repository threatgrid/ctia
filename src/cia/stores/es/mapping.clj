(ns cia.stores.es.mapping)

(def ts {:type "date" :format "basic_date_time"})

(def related
  {:confidence {:type "string"}
   :source {:type "string"}
   :relationship {:type "string"}})

(def valid-time
  {:type "object"
   :properties
   {:start_time {:type "date" :format "basic_date_time"}
    :end_time {:type "date" :format "basic_date_time"}}})

(def observable
  {:type "object"
   :properties
   {:type {:type "string"}
    :value {:type "string"}}})

(def related-indicators
  {:type "object"
   :properties
   (assoc related
          :indicator {:type "string"})})

(def related-judgements
  {:type "object"
   :properties
   (assoc related
          :judgement {:type "string"})})

(def related-coas
  {:type "object"
   :properties
   (assoc related
          :COA {:type "string"})})

(def related-campaigns
  {:type "object"
   :properties
   (assoc related
          :campaign {:type "string"})})

(def specifications
  {:type "object"
   :properties
   {:judgements {:type "string"}
    :required_judgements related-judgements
    :query {:type "string"}
    :variables {:type "string"}
    :snort_sig {:type "string"}
    :SIOC {:type "string"}
    :open_IOC {:type "string"}}})

(def sighting
  {:type "object"
   :properties
   {:timestamp ts
    :source {:type "string"}
    :reference {:type "string"}
    :confidence {:type "string"}
    :description {:type "string"}
    :related_judgements related-judgements}})

(def judgement-mapping
  {"judgement"
   {:properties
    {:id {:type "string"}
     :observable observable
     :disposition {:type "long"}
     :disposition_name {:type "string"}
     :source {:type "string"}
     :priority {:type "integer"}
     :confidence {:type "string"}
     :severity {:type "integer"}
     :valid_time valid-time
     :reason {:type "string"}
     :source_uri {:type "string"}
     :reason_uri {:type "string"}
     :indicators related-indicators
     :owner {:type "string"
             :created ts
             :modified ts}}}})

(def indicator-mapping
  {"indicator"
   {:properties
    {:id {:type "string"}
     :alternate_ids {:type "string"}
     :version {:type "integer"}
     :negate {:type "boolean"}
     :type {:type "string"}
     :observable observable
     :judgements related-judgements
     :composite_indicator_expression {:type "object"
                                      :operator {:type "string"}
                                      :indicators {:type "string"}}
     :indicated_TTP {:type "object"}
     :likely_impact {:type "string"}
     :suggested_COAs related-coas
     :confidence {:type "string"}
     :sightings sighting
     :related_indicators related-indicators
     :related_campaigns related-campaigns
     :related_COAs related-coas
     :kill_chain_phases {:type "string"}
     :test_mechanisms {:type "string"}
     :producer {:type "string"}
     :specifications specifications
     :owner {:type "string"}
     :created ts
     :modified ts}}})

(def mappings
  (merge {}
         judgement-mapping
         indicator-mapping))
