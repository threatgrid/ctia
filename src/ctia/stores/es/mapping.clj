(ns ctia.stores.es.mapping)

;; This provides a reasonable default mapping for all of our entities.
;; It aschews nested objects since they are performance risks, and
;; restricts the _all to a minimal set.

;; not that fields with the same name, nee to have the same mapping,
;; even in different entities.  That means

(def ts
  "A mapping for our timestamps, which should all be ISO8601 format"
  {:type "date" :format "date_time"})

(def text
  "A mapping for free text, or markdown, fields.  They will be
  analyzed and treated like prose."
  {:type "text"
   :fielddata true
   :analyzer "text_analyzer"
   :search_quote_analyzer "text_analyzer"
   :search_analyzer "search_analyzer"})

(def all_text
  "The same as the `text` maping, but will be included in the _all field"
  (assoc text :include_in_all true))

(def token
  "A mapping for fields whose value should be treated like a symbol.
  They will not be analyzed, and they will be lowercased."
  {:type "text"
   ;; TODO use token and disable fielddata once token analyzer is supported
   :fielddata true
   :analyzer "token_analyzer"
   :search_analyzer "token_analyzer"})

(def all_token
  "The same as the `token` mapping, but will be included in the _all field"
  {:type "text"
   :fielddata true
   :analyzer "token_analyzer"
   :search_analyzer "token_analyzer"
   :include_in_all true})

(def base-entity-mapping
  {:id all_token
   :type token
   :schema_version token
   :revision {:type "long"}
   :external_ids all_token
   :timestamp ts
   :language token
   :tlp token})

(def describable-entity-mapping
  {:title (assoc all_text
                 :fields {:whole all_token})
   :short_description all_text
   :description all_text})

(def sourcable-entity-mapping
  {:source token
   :source_uri token})

(def stored-entity-mapping
  {:owner token
   :groups token
   :authorized_users token
   :authorized_groups token
   :created ts
   :modified ts})

(def valid-time
  {:properties
   {:start_time ts
    :end_time ts}})

(def related
  {:confidence token
   :source token
   :relationship token})

(def related-indicators
  {:properties
   (assoc related
          :indicator_id all_token)})

(def related-coas
  {:properties
   (assoc related
          :COA_id all_token)})

(def related-actors
  {:properties (assoc related
                      :actor_id all_token)})

(def related-ttps
  {:properties (assoc related
                      :TTP_id all_token)})

(def related-incidents
  {:properties (assoc related
                      :incident_id all_token)})

(def attack-pattern
  {:properties
   {:title (merge all_text
                  {:fields {:whole all_token}})
    :short_description all_text
    :description all_text
    :capec_id token}})

(def malware-instance
  {:properties
   {:title (merge all_text
                  {:fields {:whole all_token}})
    :description all_text
    :short_description all_text
    :type token}})

(def observable
  {:type "object"
   :properties
   {:type token
    :value all_token}})

(def behavior
  {:properties
   {:attack_patterns attack-pattern
    :malware_type malware-instance}})

(def tool
  {:properties
   {:description all_text
    :type token
    :references token
    :vendor token
    :version token
    :service_pack token}})

(def infrastructure
  {:properties
   {:title (merge all_text
                  {:fields {:whole all_token}})
    :description all_text
    :short_description all_text
    :type token}})

(def related-identities
  {:properties (assoc related
                      :identity all_token
                      :information_source token)})

(def tg-identity
  {:properties
   {:description all_text
    :related_identities related-identities}})

(def victim-targeting
  {:properties
   {:identity tg-identity
    :targeted_systems token
    :targeted_information token
    :targeted_observables observable}})

(def resource
  {:properties
   {:tools tool
    :infrastructure infrastructure
    :personas tg-identity}})

(def activity
  {:properties
   {:date_time ts
    :description all_text}})

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
   {:security_compromise token
    :data_encrypted {:type "boolean"}}})

(def property-affected
  {:properties
   {:property token
    :description_of_effect text
    :type_of_availability_loss token
    :duration_of_availability_loss token
    :non_public_data_compromised non-public-data-compromised}})

(def affected-asset
  {:properties
   {:type token
    :description all_text
    :ownership_class token
    :management_class token
    :location_class token
    :property_affected property-affected
    :identifying_observables observable}})

(def direct-impact-summary
  {:properties
   {:asset_losses token
    :business_mission_distruption token
    :response_and_recovery_costs token}})

(def indirect-impact-summary
  {:properties
   {:loss_of_competitive_advantage token
    :brand_and_market_damage token
    :increased_operating_costs token
    :local_and_regulatory_costs token}})

(def loss-estimation
  {:properties
   {:amount {:type "long"}
    :iso_currency_code token}})

(def total-loss-estimation
  {:properties
   {:initial_reported_total_loss_estimation loss-estimation
    :actual_total_loss_estimation loss-estimation
    :impact_qualification token
    :effects token}})

(def impact-assessment
  {:properties
   {:direct_impact_summary direct-impact-summary
    :indirect_impact_summary indirect-impact-summary
    :total_loss_estimation total-loss-estimation
    :impact_qualification token
    :effects token}})

(def contributor
  {:properties
   {:role token
    :name token
    :email token
    :phone token
    :organization token
    :date ts
    :contribution_location token}})

(def coa-requested
  {:properties
   {:time ts
    :contributors contributor
    :COA all_token}})

(def history
  {:properties
   {:action_entry coa-requested
    :journal_entry token}})

(def vulnerability
  {:properties
   {:title (merge all_text
                  {:fields {:whole all_token}})
    :description all_text
    :is_known {:type "boolean"}
    :is_public_acknowledged {:type "boolean"}
    :short_description all_text
    :cve_id token
    :osvdb_id token
    :source token
    :discovered_datetime ts
    :published_datetime ts
    :affected_software token
    :references token}})

(def weakness
  {:properties
   {:description all_text
    :cwe_id token}})

(def configuration
  {:properties
   {:description all_text
    :short_description all_text
    :cce_id token}})

(def action-type
  {:properties
   {:type token}})

(def target-type
  {:properties
   {:type token
    :specifiers token}})

(def actuator-type
  {:properties
   {:type token
    :specifiers token}})

(def additional-properties
  {:properties
   {:context token}})

(def modifier-type
  {:properties
   {:delay ts
    :duration ts
    :frequency token
    :id token
    :time valid-time
    :response token
    :source token
    :destination token
    :method token
    :search token
    :location token
    :option token
    :additional_properties additional-properties}})

(def open-c2-coa
  {:properties
   {:type token
    :id token
    :action action-type
    :target target-type
    :actuator actuator-type
    :modifiers modifier-type}})

(def judgement-mapping
  {"judgement"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:observable observable
      :disposition {:type "long"}
      :disposition_name token
      :priority {:type "long"}
      :confidence token
      :severity token
      :valid_time valid-time
      :reason all_text
      :reason_uri token})}})

(def feedback-mapping
  {"feedback"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:entity_id all_token
      :feedback {:type "integer"}
      :reason all_text})}})

(def indicator-mapping
  {"indicator"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:valid_time valid-time
      :producer token
      :negate {:type "boolean"}
      :indicator_type token
      :alternate_ids token
      :tags all_token
      :composite_indicator_expression {:type "object"
                                       :properties
                                       {:operator token
                                        :indicator_ids token}}
      :likely_impact token
      :confidence token
      :kill_chain_phases token
      :test_mechanisms token
      :specification {:enabled false}})}})

(def ttp-mapping
  {"ttp"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:ttp token
      :valid_time valid-time
      :intended_effect token
      :behavior behavior
      :resources resource
      :victim_targeting victim-targeting
      :kill_chains token
      :ttp_type token
      :expires ts})}})

(def actor-mapping
  {"actor"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:valid_time valid-time
      :actor_type token
      :identity tg-identity
      :motivation token
      :sophistication token
      :intended_effect token
      :planning_and_operational_support token
      :related_indicators related-indicators
      :confidence token})}})

(def campaign-mapping
  {"campaign"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:valid_time valid-time
      :campaign_type token
      :names all_token
      :intended_effect token
      :status token
      :confidence token
      :activity activity})}})

(def coa-mapping
  {"coa"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:valid_time valid-time
      :stage token
      :coa_type token
      :objective text
      :impact token
      :cost token
      :efficacy token
      :structured_coa_type token
      :open_c2_coa open-c2-coa
      :related_COAs related-coas})}})


(def incident-mapping
  {"incident"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:valid_time valid-time
      :confidence token
      :status token
      :incident_time incident-time
      :categories token
      :reporter token
      :responder token
      :coordinator token
      :victim token
      :affected_assets affected-asset
      :impact_assessment impact-assessment
      :security_compromise token
      :discovery_method token
      :COA_requested coa-requested
      :COA_taken coa-requested
      :contact token
      :history history
      :related_indicators related-indicators
      :related_observables observable
      :leveraged_TTPs related-ttps
      :attributed_actors related-actors
      :related_incidents related-incidents
      :intended_effect token})}})


(def exploit-target-mapping
  {"exploit-target"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:valid_time valid-time
      :vulnerability vulnerability
      :weakness weakness
      :configuration configuration})}})

(def identity-mapping
  {"identity"
   {:dynamic "strict"
    :include_in_all false
    :properties
    {:id all_token
     :role token
     :capabilities token
     :login token
     :groups token}}})

(def observed-relation
  {:dynamic "strict"
   :properties
   {:id token
    :timestamp ts
    :origin token
    :origin_uri token
    :relation token
    :relation_info {:type "object"
                    :include_in_all false
                    :dynamic true}
    :source observable
    :related observable}})

(def sighting-target
  {:dynamic "strict"
   :properties
   {:type token
    :os token
    :observables observable
    :properties_data_tables token}})

(def sighting-mapping
  {"sighting"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:observed_time valid-time
      :count {:type "long"}
      :sensor token
      :target sighting-target
      :reference token
      :confidence token
      :observables observable
      :observables_hash token
      :relations observed-relation})}})

(def data-table-mapping
  {"data-table"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:valid_time valid-time
      :row_count {:type "long"}
      :columns {:enabled false}
      :rows {:enabled false}})}})

(def relationship-mapping
  {"relationship"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:relationship_type token
      :source_ref all_token
      :target_ref all_token})}})

(def dynamic-templates
  [{:date_as_datetime {:match "*"
                       :match_mapping_type "date"
                       :mapping ts}}
   {:string_not_analyzed {:match "*"
                          :match_mapping_type "string"
                          :mapping token}}])

(def event-mapping
  {"event"
   {:dynamic_templates dynamic-templates
    :properties
    {:owner token
     :groups token
     :timestamp ts
     :entity {:type "object"}
     :id token
     :http-params {:type "object"}
     :type token
     :fields {:type "object"}
     :judgement_id token}}})

(def store-settings
  {:number_of_replicas 1
   :number_of_shards 1
   :analysis
   {:filter
    {:token_len {:max 255
                 :min 0
                 :type "length"}
     :english_stop {:type "stop"
                    :stopwords "_english_"}}
    :analyzer
    {:default_search ;; same as text_analyzer
     {:type "custom"
      :tokenizer "standard"
      :filter ["lowercase" "english_stop"]}
     :text_analyzer
     {:type "custom"
      :tokenizer "standard"
      :filter ["lowercase"]}
     :search_analyzer
     {:type "custom"
      :tokenizer "standard"
      :filter ["lowercase" "english_stop"]}
     :token_analyzer
     {:filter ["token_len" "lowercase"]
      :tokenizer "keyword"
      :type "custom"}}}})

(def store-mappings
  {:judgement judgement-mapping
   :relationship relationship-mapping
   :indicator indicator-mapping
   :ttp ttp-mapping
   :feedback feedback-mapping
   :actor actor-mapping
   :campaign campaign-mapping
   :coa coa-mapping
   :data-table data-table-mapping
   :incident incident-mapping
   :exploit-target exploit-target-mapping
   :sighting sighting-mapping
   :identity identity-mapping
   :event event-mapping})
