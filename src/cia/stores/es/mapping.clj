(ns cia.stores.es.mapping)

(def ts {:type "date" :format "date_time"})
(def string {:type "string" :index "not_analyzed"})


(def related
  {:confidence {:type "string"}
   :source {:type "string"}
   :relationship {:type "string"}})

(def valid-time
  {:type "nested"
   :properties
   {:start_time ts
    :end_time ts}})

(def attack-pattern
  {:type "nested"
   :properties
   {:description string
    :capec_id string}})

(def malware-instance
  {:type "nested"
   :properties
   {:description string
    :malware_type string}})

(def observable
  {:type "nested"
   :properties
   {:type string
    :value string}})

(def behavior
  {:type "nested"
   :properties
   {:attack_patterns attack-pattern
    :malware_type malware-instance}})

(def tool
  {:type "nested"
   :properties
   {:description string
    :type string
    :references string
    :vendor string
    :version string
    :service_pack string}})

(def infrastructure
  {:type "nested"
   :properties
   {:description string
    :type string}})

(def related-identities
  {:type "nested"
   :properties (assoc related
                      :identity string)})

(def identity
  {:type "nested"
   :properties
   {:description string
    :related_identities related-identities}})

(def victim-targeting
  {:type "nested"
   :properties
   {:identity identity
    :targeted_systems string
    :targeted_information string
    :targeted_observables observable}})

(def resource
  {:type "nested"
   :tools tool
   :infrastructure infrastructure
   :providers identity})

(def related-indicators
  {:type "nested"
   :properties
   (assoc related
          :indicator string)})

(def related-judgements
  {:type "nested"
   :properties
   (assoc related
          :judgement string)})

(def related-coas
  {:type "nested"
   :properties
   (assoc related
          :COA string)})

(def related-campaigns
  {:type "nested"
   :properties
   (assoc related
          :campaign string)})

(def related-exploit-targets
  {:type "nested"
   :properties (assoc related
                      :exploit_target string)})
(def related-ttps
  {:type "nested"
   :properties (assoc related
                      :ttp string)})

(def specifications
  {:type "nested"
   :properties
   {:judgements string
    :required_judgements related-judgements
    :query string
    :variables string
    :snort_sig string
    :SIOC string
    :open_IOC string}})

(def sighting
  {:type "nested"
   :properties
   {:timestamp ts
    :source string
    :reference string
    :confidence string
    :description string
    :related_judgements related-judgements}})

(def judgement-mapping
  {"judgement"
   {:properties
    {:id string
     :observable observable
     :disposition {:type "long"}
     :disposition_name string
     :source string
     :priority {:type "integer"}
     :confidence string
     :severity {:type "integer"}
     :valid_time valid-time
     :reason string
     :source_uri string
     :reason_uri string
     ;;:indicators related-indicators TBD check if varying
     :indicators {:type "nested" :enabled false}
     :owner string
     :created ts
     :modified ts}}})

(def feedback-mapping
  {"feedback"
   {:properties
    {:id string
     :judgement string
     :source string
     :feedback {:type "integer"}
     :reason string
     :owner string
     :created ts
     :modified ts}}})

(def indicator-mapping
  {"indicator"
   {:properties
    {:id string
     :alternate_ids string
     :version {:type "integer"}
     :negate {:type "boolean"}
     :type string
     :observable observable
     ;; :judgements related-judgements TBD check if varying
     :judgements {:type "nested" :enabled false}
     :composite_indicator_expression {:type "nested"
                                      :properties
                                      {:operator string
                                       :indicators string}}
     :indicated_TTP {:type "nested"}
     :likely_impact string
     :suggested_COAs related-coas
     :confidence string
     :sightings sighting
     :related_indicators related-indicators
     :related_campaigns related-campaigns
     :related_COAs related-coas
     :kill_chain_phases string
     :test_mechanisms string
     :producer string
     :specifications specifications
     :owner string
     :created ts
     :modified ts}}})

(def ttp-mapping
  {"ttp"
   {:properties
    {:id string
     :valid_time valid-time
     :version string
     :intended_effect string
     :behavior behavior
     :resources resource
     :victim_targeting victim-targeting
     :exploit_targets related-exploit-targets
     :related_TTPs related-ttps
     :source string
     :type string
     :expires ts
     :indicators string}}})


(def mappings
  (merge {}
         judgement-mapping
         indicator-mapping
         feedback-mapping))
