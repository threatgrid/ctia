(ns ctia.stores.es.mapping)

;; This provides a reasonable default mapping for all of our entities.
;; It aschews nested objects since they are performance risks, and
;; restricts the _all to a minimal set.

;; not that fields with the same name, nee to have the same mapping,
;; even in different entities.  That means

(def ts
  "A mapping for our timestamps, which should all be ISO8601 format"
  {:type "date"
   :format "date_time"})

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

(def external-reference
  {:properties
   {:source_name token
    :description all_text
    :url token
    :hashes token
    :external_id token}})

(def base-entity-mapping
  {:id all_token
   :type token
   :schema_version token
   :revision {:type "long"}
   :external_ids all_token
   :external_references external-reference
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

(def kill-chain-phase
  {:properties
   {:kill_chain_name token
    :phase_name token}})

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

(def related-incidents
  {:properties (assoc related
                      :incident_id all_token)})

(def observable
  {:type "object"
   :properties
   {:type token
    :value all_token}})

(def assertion
  {:properties
   {:name all_text
    :value all_text}})

(def related-identities
  {:properties (assoc related
                      :identity all_token
                      :information_source token)})

(def tg-identity
  {:properties
   {:description all_text
    :related_identities related-identities}})

(def activity
  {:properties
   {:date_time ts
    :description all_text}})

(def incident-time
  {:properties
   {:opened ts
    :discovered ts
    :reported ts
    :remediated ts
    :closed ts
    :rejected ts}})

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

(def observed-relation
  {:dynamic false
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
  {:dynamic false
   :properties
   {:type token
    :observed_time valid-time
    :os token
    :observables observable}})

(def sighting-sensor
  {:dynamic false
   :properties
   {:type token
    :os token
    :observables observable}})

(def embedded-data-table
  {:dynamic false
   :properties
   {:row_count {:type "long"}
    :columns {:enabled false}
    :rows {:enabled false}}})

(def texts
  {:properties {:type token
                :text text}})

(def dynamic-templates
  [{:date_as_datetime {:match "*"
                       :match_mapping_type "date"
                       :mapping ts}}
   {:string_not_analyzed {:match "*"
                          :match_mapping_type "string"
                          :mapping token}}])

(def store-settings
  {:number_of_replicas 1
   :number_of_shards 1
   :analysis
   {:filter
    {:token_len {:max 255
                 :min 0
                 :type "length"}
     :english_stop {:type "stop"
                    :stopwords "_english_"}
     ;; word_delimiter filter enables to improve tokenization https://www.elastic.co/guide/en/elasticsearch/reference/5.6/analysis-word-delimiter-tokenfilter.html
     ;; standard tokenization do not split www.domain.com, which is done here to enable search on 'domain', but we avoid splitting on numbers in words like j2ee
     ;; it also removes english possessive
     :ctia_stemmer {:type "word_delimiter"
                    :generate_number_parts false
                    :preserve_original true
                    :split_on_numerics false
                    :split_on_case_change false
                    :stem_english_possessive true}
     :english_stemmer {:type "stemmer"
                       :language "english"}}
    ;; when applying filters, order matters
    :analyzer
    {:default ;; same as text_analyzer
     {:type "custom"
      :tokenizer "standard"
      :filter ["lowercase"
               "ctia_stemmer"
               "english_stop"
               "english_stemmer"]}
     :text_analyzer
     {:type "custom"
      :tokenizer "standard"
      :filter ["lowercase"
               "ctia_stemmer"
               "english_stemmer"]}
     :search_analyzer
     {:type "custom"
      :tokenizer "standard"
      :filter ["lowercase"
               "ctia_stemmer"
               "english_stop"
               "english_stemmer"]}
     :token_analyzer
     {:filter ["token_len" "lowercase"]
      :tokenizer "keyword"
      :type "custom"}}}})
