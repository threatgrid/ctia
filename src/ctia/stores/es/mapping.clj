(ns ctia.stores.es.mapping
  (:refer-clojure :exclude [identity]))

;; This provides a reasonable default mapping for all of our entities.
;; It aschews nested objects since they are performance risks, and
;; restricts the _all to a minimal set.

;; Note that fields with the same name, need to have the same mapping,
;; even in different entities.

(def float-type
  {:type "float"})

(def long-type
  {:type "long"})

(def boolean-type
  {:type "boolean"})

(def integer-type
  {:type "integer"})

(def ts
  "A mapping for our timestamps, which should all be ISO8601 format"
  {:type "date"
   :format "date_time"})

(def text
  "A mapping for free text, or markdown, fields.  They will be
  analyzed and treated like prose."
  {:type "text"
   :analyzer "text_analyzer"
   :search_quote_analyzer "text_analyzer"
   :search_analyzer "search_analyzer"})

(def token
  "A mapping for fields whose value should be treated like a symbol.
  They will not be analyzed, and they will be lowercased."
  {:type "keyword"
   :normalizer "lowercase_normalizer"})

(def sortable-text
  (assoc text
         :fields {:whole token}))

(def external-reference
  {:properties
   {:source_name token
    :description text
    :url token
    :hashes token
    :external_id token}})

(def base-entity-mapping
  {:id token
   :type token
   :schema_version token
   :revision long-type
   :external_ids token
   :external_references external-reference
   :timestamp ts
   :language token
   :tlp token})

(def describable-entity-mapping
  {:title sortable-text
   :short_description text
   :description text})

(def sourcable-entity-mapping
  {:source token
   :source_uri token})

(def stored-entity-mapping
  {:client_id token
   :owner token
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
          :indicator_id token)})

(def related-coas
  {:properties
   (assoc related
          :COA_id token)})

;; TODO: remove unused var
#_(def related-actors
  {:properties (assoc related
                      :actor_id token)})

;; TODO: remove unused var
#_(def related-assets
  {:properties (assoc related :asset_id token)})

;; TODO: remove unused var
#_(def related-asset-mappings
  {:properties (assoc related :asset_mapping_id token)})

;; TODO: remove unused var
#_(def related-asset-properties
  {:properties (assoc related :asset_properties_id token)})

;; TODO: remove unused var
#_(def related-target-record
  {:properties (assoc related :target_record_id token)})

;; TODO: remove unused var
#_(def related-incidents
  {:properties (assoc related
                      :incident_id token)})

(def observable
  {:type "object"
   :properties
   {:type token
    :value token}})

(def identity
  {:dynamic false
   :properties
   {:observables observable}})

(def assertion
  {:properties
   {:name token
    :value text}})

(def related-identities
  {:properties (assoc related
                      :identity token
                      :information_source token)})

(def tg-identity
  {:properties
   {:description text
    :related_identities related-identities}})

(def activity
  {:properties
   {:date_time ts
    :description text}})

(def incident-time
  {:properties
   {:opened ts
    :discovered ts
    :reported ts
    :remediated ts
    :closed ts
    :rejected ts}})

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
                    :dynamic true}
    :source observable
    :related observable}})

(def target-record-target
  {:dynamic false
   :properties
   {:type          token
    :observed_time valid-time
    :os            token
    :observables   observable
    :internal      boolean-type
    :sensor        token}})

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

(def store-settings
  {:number_of_replicas 1
   :number_of_shards 1
   :analysis
   {:normalizer
    {:lowercase_normalizer
     {:type "custom"
      :char_filter []
      :filter ["lowercase"]}}
    :filter {
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
               "english_stemmer"]}}}})
