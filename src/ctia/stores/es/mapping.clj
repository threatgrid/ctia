(ns ctia.stores.es.mapping)

;; This provides a reasonable default mapping for all of our entities.
;; It aschews nested objects since they are performance risks, and
;; restricts the _all to a minimal set.

;; not that fields with the same name, nee to have the same mapping,
;; even in different entities.  That means

(def ts {:type "date" :format "date_time"})

(def string {:type "string" :index "not_analyzed"})
(def all_string {:type "string"
                 :index "not_analyzed"
                 :include_in_all true})

(def text {:type "string"})
(def all_text {:type "string"
               :include_in_all true})

(def token {:type "string" :index "not_analyzed"})
(def all_token {:type "string" :index "not_analyzed"
                :include_in_all true})

(def base-entity-mapping
  {:id all_token
   :type token
   :schema_version token
   :uri all_token
   :revision {:type "long"}
   :external_ids all_token
   :timestamp ts
   :language token
   :tlp token
   })

(def describable-entity-mapping
  {:title all_string
   :short_description all_text
   :description all_text})

(def sourcable-entity-mapping
  {:source token
   :source_uri token})

(def stored-entity-mapping
  {:owner string
   :created ts
   :modified ts})

(def related
  {:confidence string
   :source string
   :relationship string})

(def valid-time
  {:properties
   {:start_time ts
    :end_time ts}})

(def attack-pattern
  {:properties
   {:title string
    :short_description all_text
    :description all_text
    :capec_id string}})

(def malware-instance
  {:properties
   {:title string
    :description all_text
    :short_description all_text
    :type string}})

(def observable
  {:type "object"
   :properties
   {:type string
    :value all_string}})

(def behavior
  {:properties
   {:attack_patterns attack-pattern
    :malware_type malware-instance}})

(def tool
  {:properties
   {:description all_text
    :type string
    :references string
    :vendor string
    :version string
    :schema_version string
    :service_pack string}})

(def infrastructure
  {:properties
   {:title string
    :description all_text
    :short_description string
    :type string}})

(def related-identities
  {:properties (assoc related
                      :identity all_string
                      :information_source string)})

(def related-actors
  {:properties (assoc related
                      :actor_id all_string)})

(def tg-identity
  {:properties
   {:description all_text
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
    :personas tg-identity}})

(def activity
  {:properties
   {:date_time ts
    :description all_text}})

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
          :COA_id all_string)})

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

(def specification
  {:properties
   {:type string
    :judgements string
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
    :description all_text
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
    :COA all_string}})

(def history
  {:properties
   {:action_entry coa-requested
    :journal_entry string}})

(def vulnerability
  {:properties
   {:title all_text
    :description all_text
    :is_known {:type "boolean"}
    :is_public_acknowledged {:type "boolean"}
    :short_description all_text
    :cve_id string
    :osvdb_id string
    :source string
    :discovered_datetime ts
    :published_datetime ts
    :affected_software string
    :references string}})

(def weakness
  {:properties
   {:description all_text
    :cwe_id string}})

(def configuration
  {:properties
   {:description all_text
    :short_description all_text
    :cce_id string}})

(def action-type
  {:properties
   {:type string}})

(def target-type
  {:properties
   {:type string
    :specifiers string}})

(def actuator-type
  {:properties
   {:type string
    :specifiers string}})

(def additional-properties
  {:properties
   {:context string}})

(def modifier-type
  {:properties
   {:delay ts
    :duration ts
    :frequency string
    :id string
    :time valid-time
    :response string
    :source string
    :destination string
    :method string
    :search string
    :location string
    :option string
    :additional_properties additional-properties}})

(def open-c2-coa
  {:properties
   {:type string
    :id string
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
      :confidence string
      :severity {:type "long"}
      :valid_time valid-time
      :reason all_text
      :reason_uri string
      :indicators related-indicators})}})

(def verdict-mapping
  {"verdict"
   {:dynamic "strict"
    :include_in_all false
    :properties
    {:id all_token
     :type token
     :schema_version string
     :judgement_id token
     :observable observable
     :disposition {:type "long"}
     :disposition_name token
     :owner string
     :created ts}}})

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
      :producer string
      :negate {:type "boolean"}
      :indicator_type token
      :alternate_ids token
      :tags all_token
      :judgements related-judgements
      :composite_indicator_expression {:type "object"
                                       :properties
                                       {:operator string
                                        :indicator_ids token}}
      :indicated_TTP related-ttps
      :likely_impact token
      :suggested_COAs related-coas
      :confidence token
      :sightings related-sightings
      :related_indicators related-indicators
      :related_campaigns related-campaigns
      :related_COAs related-coas
      :kill_chain_phases token
      :test_mechanisms token
      :specification specification
      })}})

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
      :intended_effect string
      :behavior behavior
      :resources resource
      :victim_targeting victim-targeting
      :exploit_targets related-exploit-targets
      :related_TTPs related-ttps
      :kill_chains string
      :ttp_type string
      :expires ts
      :indicators related-indicators})}})

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
      :motivation string
      :sophistication string
      :intended_effect string
      :planning_and_operational_support string
      :observed_TTPs related-ttps
      :associated_campaigns related-campaigns
      :associated_actors related-actors
      :confidence string})}})

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
      :names all_string
      :indicators related-indicators
      :intended_effect string
      :status string
      :related_TTPs related-ttps
      :related_incidents related-incidents
      :attribution related-actors
      :associated_campaigns related-campaigns
      :confidence string
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
      :stage string
      :coa_type string
      :objective text
      :impact string
      :cost string
      :efficacy string
      :related_COAs related-coas
      :structured_coa_type string
      :open_c2_coa open-c2-coa})}})

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
      :confidence string
      :status string
      :incident_time incident-time
      :categories string
      :reporter string
      :responder string
      :coordinator string
      :victim string
      :affected_assets affected-asset
      :impact_assessment impact-assessment
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
      :intended_effect string})}})

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
      :configuration configuration
      :potential_COAs related-coas
      :related_exploit_targets related-exploit-targets})}})

(def identity-mapping
  {"identity"
   {:dynamic "strict"
    :include_in_all false
    :properties
    {:id all_token
     :role token
     :capabilities string
     :login string}}})

(def observed-relation
  {:dynamic "strict"
   :properties
   {:id string
    :timestamp ts
    :origin string
    :origin_uri string
    :relation string
    :relation_info {:type "object"
                    :include_in_all false
                    :dynamic true}
    :source observable
    :related observable}})

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
      :sensor string
      :reference string
      :confidence string
      :observables observable
      :observables_hash string
      :indicators related-indicators
      :incidents related-incidents
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
      :columns {:type "object" :enabled false}
      :rows {:type "object" :enabled false}})}})

(def bundle-mapping
  {"bundle"
   {:dynamic "strict"
    :include_in_all false
    :properties
    (merge
     base-entity-mapping
     describable-entity-mapping
     sourcable-entity-mapping
     stored-entity-mapping
     {:valid_time valid-time

      :actors {:type "object" :enabled false}
      :campaigns {:type "object" :enabled false}
      :coas {:type "object" :enabled false}
      :exploit-targets {:type "object" :enabled false}
      :data-tables {:type "object" :enabled false}
      :feedbacks {:type "object" :enabled false}
      :incidents {:type "object" :enabled false}
      :indicators {:type "object" :enabled false}
      :judgements {:type "object" :enabled false}
      :relationships {:type "object" :enabled false}
      :sightings {:type "object" :enabled false}
      :ttps {:type "object" :enabled false}
      :verdicts {:type "object" :enabled false}

      :actor_refs string
      :campaign_refs string
      :coa_refs string
      :data-table_refs string
      :exploit-target_refs string
      :feedback_refs string
      :incident_refs string
      :indicator_refs string
      :judgement_refs string
      :relationship_refs string
      :sighting_refs string
      :ttp_refs string
      :verdict_refs string})}})

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
     {:relationship_type string
      :source_ref all_token
      :target_ref all_token})}})

(def store-mappings
  (merge {}
         judgement-mapping
         relationship-mapping
         verdict-mapping
         indicator-mapping
         ttp-mapping
         feedback-mapping
         actor-mapping
         campaign-mapping
         coa-mapping
         data-table-mapping
         incident-mapping
         exploit-target-mapping
         sighting-mapping
         identity-mapping
         bundle-mapping))
