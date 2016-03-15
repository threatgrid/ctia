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
                      :identity_id string)})

(def related-actors
  {:type "nested"
   :properties (assoc related
                      :actor_id string)})

(def tg-identity
  {:type "nested"
   :properties
   {:description string
    :related_identities related-identities}})

(def victim-targeting
  {:type "nested"
   :properties
   {:identity tg-identity
    :targeted_systems string
    :targeted_information string
    :targeted_observables observable}})

(def resource
  {:type "nested"
   :properties
   {:tools tool
    :infrastructure infrastructure
    :providers identity}})

(def activity
  {:type "nested"
   :properties
   {:date_time ts
    :description string}})

(def related-indicators
  {:type "nested"
   :properties
   (assoc related
          :indicator_id string)})

(def related-judgements
  {:type "nested"
   :properties
   (assoc related
          :judgement_id string)})

(def related-coas
  {:type "nested"
   :properties
   (assoc related
          :COA_id string)})

(def related-campaigns
  {:type "nested"
   :properties
   (assoc related
          :campaign_id string)})

(def related-exploit-targets
  {:type "nested"
   :properties (assoc related
                      :exploit_target_id string)})
(def related-ttps
  {:type "nested"
   :properties (assoc related
                      :ttp_id string)})

(def related-incidents
  {:type "nested"
   :properties (assoc related
                      :incident_id string)})

(def related-sightings
  {:type "nested"
   :properties (assoc related
                      :sighting_id string)})

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

(def incident-time
  {:type "nested"
   :properties
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
  {:type "nested"
   :properties
   {:security_compromise string
    :data_encrypted {:type "boolean"}}})

(def property-affected
  {:type "nested"
   :properties
   {:property string
    :description_of_effect string
    :type_of_availability_loss string
    :duration_of_availability_loss string
    :non_public_data_compromised non-public-data-compromised}})

(def affected-asset
  {:type "nested"
   :properties
   {:type string
    :description string
    :ownership_class string
    :management_class string
    :location_class string
    :property_affected property-affected
    :identifying_observables observable}})

(def direct-impact-summary
  {:type "nested"
   :properties
   {:asset_losses string
    :business_mission_distruption string
    :response_and_recovery_costs string}})

(def indirect-impact-summary
  {:type "nested"
   :properties
   {:loss_of_competitive_advantage string
    :brand_and_market_damage string
    :increased_operating_costs string
    :local_and_regulatory_costs string}})

(def loss-estimation
  {:type "nested"
   :properties
   {:amount {:type "long"}
    :iso_currency_code string}})

(def total-loss-estimation
  {:type "nested"
   :properties
   {:initial_reported_total_loss_estimation loss-estimation
    :actual_total_loss_estimation loss-estimation
    :impact_qualification string
    :effects string}})

(def impact-assessment
  {:type "nested"
   :properties
   {:direct_impact_summary direct-impact-summary
    :indirect_impact_summary indirect-impact-summary
    :total_loss_estimation total-loss-estimation
    :impact_qualification string
    :effects string}})

(def contributor
  {:type "nested"
   :properties
   {:role string
    :name string
    :email string
    :phone string
    :organization string
    :date ts
    :contribution_location string}})

(def coa-requested
  {:type "nested"
   :properties
   {:time ts
    :contributors contributor
    :COA string}})

(def history
  {:type "nested"
   :properties
   {:action_entry coa-requested
    :journal_entry string}})

(def vulnerability
  {:type "nested"
   :properties
   {:title string
    :description string
    :is_known {:type "boolean"}
    :is_public_acknowledged {:type "boolean"}
    :short_description string
    :cve_id string
    :osvdb_id string
    :source string
    :discovered_datetime ts
    :published_datetime ts
    :affected_software string
    :references string}})

(def weakness
  {:type "nested"
   :properties
   {:description string
    :cwe_id string}})

(def configuration
  {:type "nested"
   :description string
   :short_description string
   :cce_id string})

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
     :indicators related-indicators
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
     :title string
     :alternate_ids string
     :version {:type "integer"}
     :negate {:type "boolean"}
     :type string
     :observable observable
     :judgements related-judgements
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
     :indicators string
     :owner string
     :created ts
     :modified ts}}})

(def actor-mapping
  {"actor"
   {:properties
    {:id string
     :valid_time valid-time
     :type string
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
   {:properties
    {:id string
     :valid_time valid-time
     :names string
     :intended_effect string
     :status string
     :related_TTPs related-ttps
     :related_incidents related-incidents
     :attribution related-actors
     :associated_campaigns related-campaigns
     :confidence string
     :activity activity
     :source string
     :type string
     :indicators related-indicators
     :owner string
     :created ts
     :modified ts}}})

(def coa-mapping
  {"coa"
   {:properties
    {:id string
     :valid_time valid-time
     :stage string
     :type string
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
   {:properties
    {:id string
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
     :COA_requested coa-requested
     :COA_taken coa-requested
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
   {:properties
    {:id string
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
   {:properties
    {:id string
     :role string
     :capabilities string
     :login string}}})

(def sighting-mapping
  {"sighting"
   {:properties
    {:id string
     :timestamp ts
     :source string
     :reference string
     :confidence string
     :related-judgements related-judgements
     :owner string
     :created ts
     :modified ts}}})

(def mappings
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
