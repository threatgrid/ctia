(ns ctia.stores.es.mapping)

;; This provides a reasonable default mapping for all of our entities.
;; It aschews nested objects since they are performance risks, and
;; restricts the _all to a minimal set.


(def ts {:type "date" :format "date_time"})

(def string {:type "string" :index "not_analyzed"})
(def all_string {:type "string"
                 :index "not_analyzed"
                 :include_in_all true})

(def text {:type "string"})
(def all_text {:type "string" :copy_to "_all"})

(def related
  {:confidence {:type "string"}
   :source {:type "string"}
   :relationship {:type "string"}})

(def valid-time
  {:properties
   {:start_time ts
    :end_time ts}})

(def attack-pattern
  {:properties
   {:description text
    :capec_id string}})

(def malware-instance
  {:properties
   {:description text
    :malware_type string}})

(def observable
  {:type "object"
   :properties
   {:type string
    :value all_string}})

(def nested-observable
  {:type "nested"
   :include_in_all true
   :properties
   {:type string
    :value all_string}})

(def behavior
  {:properties
   {:attack_patterns attack-pattern
    :malware_type malware-instance}})

(def tool
  {:properties
   {:description text
    :type string
    :references string
    :vendor string
    :version string
    :service_pack string}})

(def infrastructure
  {:properties
   {:description text
    :type string}})

(def related-identities
  {:properties (assoc related
                      :identity_id all_string)})

(def related-actors
  {:properties (assoc related
                      :actor_id all_string)})

(def tg-identity
  {:properties
   {:description text
    :related_identities related-identities}})

(def victim-targeting
  {:properties
   {:identity tg-identity
    :targeted_systems string
    :targeted_information string
    :targeted_observables observable}})

(def resource
  {:properties
   {:tools tool
    :infrastructure infrastructure
    :providers identity}})

(def activity
  {:properties
   {:date_time ts
    :description text}})

(def related-indicators
  {:properties
   (assoc related
          :indicator_id all_string)})

(def related-judgements
  {:properties
   (assoc related
          :judgement_id all_string)})

(def related-coas
  {:properties
   (assoc related
          :coa_id all_string)})

(def related-campaigns
  {:properties
   (assoc related
          :campaign_id all_string)})

(def related-exploit-targets
  {:properties (assoc related
                      :exploit_target_id all_string)})
(def related-ttps
  {:properties (assoc related
                      :ttp_id all_string)})

(def related-incidents
  {:properties (assoc related
                      :incident_id all_string)})

(def related-sightings
  {:properties (assoc related
                      :sighting_id all_string)})

(def specifications
  {:properties
   {:judgements string
    :required_judgements related-judgements
    :query string
    :variables string
    :snort_sig string
    :SIOC string
    :open_IOC string}})

(def incident-time
  {:properties
   {:first_malicious_action ts
    :initial_compromise ts
    :first_data_exfiltration ts
    :incident_discovery ts
    :incident_opened ts
    :containment_achieved ts
    :restoration_achieved ts
    :incident_reported ts
    :incident_closed ts}})


(def non-public-data-compromised
  {:properties
   {:security_compromise string
    :data_encrypted {:type "boolean"}}})

(def property-affected
  {:properties
   {:property string
    :description_of_effect text
    :type_of_availability_loss string
    :duration_of_availability_loss string
    :non_public_data_compromised non-public-data-compromised}})

(def affected-asset
  {:properties
   {:type string
    :description text
    :ownership_class string
    :management_class string
    :location_class string
    :property_affected property-affected
    :identifying_observables observable}})

(def direct-impact-summary
  {:properties
   {:asset_losses string
    :business_mission_distruption string
    :response_and_recovery_costs string}})

(def indirect-impact-summary
  {:properties
   {:loss_of_competitive_advantage string
    :brand_and_market_damage string
    :increased_operating_costs string
    :local_and_regulatory_costs string}})

(def loss-estimation
  {:properties
   {:amount {:type "long"}
    :iso_currency_code string}})

(def total-loss-estimation
  {:properties
   {:initial_reported_total_loss_estimation loss-estimation
    :actual_total_loss_estimation loss-estimation
    :impact_qualification string
    :effects string}})

(def impact-assessment
  {:properties
   {:direct_impact_summary direct-impact-summary
    :indirect_impact_summary indirect-impact-summary
    :total_loss_estimation total-loss-estimation
    :impact_qualification string
    :effects string}})

(def contributor
  {:properties
   {:role string
    :name string
    :email string
    :phone string
    :organization string
    :date ts
    :contribution_location string}})

(def coa-requested
  {:properties
   {:time ts
    :contributors contributor
    :coa_id all_string}})

(def history
  {:properties
   {:action_entry coa-requested
    :journal_entry string}})

(def vulnerability
  {:properties
   {:title string
    :description text
    :is_known {:type "boolean"}
    :is_public_acknowledged {:type "boolean"}
    :short_description text
    :cve_id string
    :osvdb_id string
    :source string
    :discovered_datetime ts
    :published_datetime ts
    :affected_software string
    :references string}})

(def weakness
  {:properties
   {:description text
    :cwe_id string}})

(def configuration
  {:properties
   {:description text
    :short_description text
    :cce_id string}})

(def sighting
  {:properties
   {:timestamp ts
    :source string
    :reference string
    :confidence string
    :description all_text
    :related_judgements related-judgements}})

(def judgement-mapping
  {"judgement"
   {:dynamic false
    :include_in_all false
    :properties
    {:id string
     :type string
     :observable observable
     :disposition {:type "long"}
     :disposition_name string
     :source string
     :priority {:type "integer"}
     :confidence string
     :severity {:type "integer"}
     :valid_time valid-time
     :reason text
     :source_uri string
     :reason_uri string
     :indicators related-indicators
     :owner string
     :created ts
     :modified ts}}})

(def feedback-mapping
  {"feedback"
   {:dynamic false
    :include_in_all false
    :properties
    {:id string
     :type string
     :judgement string
     :source string
     :feedback {:type "integer"}
     :reason string
     :owner string
     :created ts
     :modified ts}}})

(def indicator-mapping
  {"indicator"
   {:dynamic false
    :include_in_all false
    :properties
    {:id string
     :type string
     :title all_string
     :description all_text
     :alternate_ids all_string
     :version string
     :negate {:type "boolean"}
     :indicator_type string
     :composite_indicator_expression {:type "nested"
                                      :properties
                                      {:operator string
                                       :indicators string}}
     :indicated_TTP {:type "nested"}
     :likely_impact string
     :suggested_COAs related-coas
     :confidence string
     :sightings related-sightings
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
   {:dynamic false
    :include_in_all false
    :properties
    {:id all_string
     :title all_text
     :description all_text
     :type string
     :valid_time valid-time
     :version string
     :intended_effect string
     :behavior behavior
     :resources resource
     :victim_targeting victim-targeting
     :exploit_targets related-exploit-targets
     :related_TTPs related-ttps
     :source string
     :ttp_type string
     :expires ts
     :indicators all_string
     :owner string
     :created ts
     :modified ts}}})

(def actor-mapping
  {"actor"
   {:dynamic false
    :include_in_all false
    :properties
    {:id all_string
     :title all_text
     :description all_text
     :type string
     :valid_time valid-time
     :actor_type string
     :source string
     :identity tg-identity
     :motivation string
     :sophistication string
     :intended_effect string
     :planning_and_operational_support string
     :observed-ttps related-ttps
     :associated_campaigns related-campaigns
     :associated_actors related-actors
     :confidence string
     :owner string
     :created ts
     :modified ts}}})

(def campaign-mapping
  {"campagin"
   {:dynamic false
    :include_in_all false
    :properties
    {:id all_string
     :type string
     :title all_text
     :description all_text
     :valid_time valid-time
     :names all_string
     :intended_effect string
     :status string
     :related_TTPs related-ttps
     :related_incidents related-incidents
     :attribution related-actors
     :associated_campaigns related-campaigns
     :confidence string
     :activity activity
     :source string
     :campaign_type string
     :indicators related-indicators
     :owner string
     :created ts
     :modified ts}}})

(def coa-mapping
  {"coa"
   {:dynamic false
    :include_in_all false
    :properties
    {:id all_string
     :type string
     :title all_text
     :description all_text
     :valid_time valid-time
     :stage string
     :coa_type string
     :objective string
     :impact string
     :cost string
     :efficacy string
     :source string
     :related_COAs related-coas
     :owner string
     :created ts
     :modified ts}}})

(def incident-mapping
  {"incident"
   {:dynamic false
    :include_in_all false
    :properties
    {:id all_string
     :type string
     :title all_text
     :description all_text
     :valid_time valid-time
     :confidence string
     :status string
     :version string
     :incident_time incident-time
     :categories string
     :reporter string
     :responder string
     :coordinator string
     :victim string
     :affected_assets affected-asset
     :impact_assessment impact-assessment
     :source string
     :security_compromise string
     :discovery_method string
     :coa_requested coa-requested
     :coa_taken coa-requested
     :contact string
     :history history
     :related_indicators related-indicators
     :related_observables observable
     :leveraged_TTPs related-ttps
     :attributed_actors related-actors
     :related_incidents related-incidents
     :intended_effect string
     :owner string
     :created ts
     :modifed ts}}})

(def exploit-target-mapping
  {"exploit-target"
   {:dynamic false
    :include_in_all false
    :properties
    {:id all_string
     :type string
     :title all_text
     :description all_text
     :valid_time valid-time
     :version string
     :vulnerability vulnerability
     :weakness weakness
     :configuration configuration
     :potential_COAs related-coas
     :source string
     :related_exploit_targets related-exploit-targets
     :owner string
     :created ts
     :modified ts}}})

(def identity-mapping
  {"identity"
   {:dynamic false
    :include_in_all false
    :properties
    {:id string
     :role string
     :capabilities string
     :login string}}})

(def sighting-mapping
  {"sighting"
   {:dynamic false
    :include_in_all false
    :properties
    {:id all_string
     :type string
     :description all_text
     :timestamp ts
     :source string
     :reference string
     :confidence string
     :related_judgements related-judgements
     :related_observables observable
     :owner string
     :created ts
     :modified ts}}})

(def store-mappings
  (merge {}
         judgement-mapping
         indicator-mapping
         feedback-mapping
         actor-mapping
         campaign-mapping
         coa-mapping
         incident-mapping
         exploit-target-mapping
         sighting-mapping
         identity-mapping))
