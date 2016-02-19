(ns cia.schemas.indicator
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema JudgementSpecification
  "An indicator based on a list of judgements.  If any of the
  Observables in it's judgements are encountered, than it may be
  matches against.  If there are any required judgements, they all
  must be matched in order for the indicator to be considered a
  match."
  {:type (s/eq "Judgement")
   :judgements [rel/JudgementReference]
   :required_judgements rel/RelatedJudgements})

(s/defschema ThreatBrainSpecification
  "An indicator which runs in threatbrain..."
  {:type (s/eq "ThreatBrain")
   :query s/Str
   :variables [s/Str] })

(s/defschema SnortSpecification
  "An indicator which runs in snort..."
  {:type (s/eq "Snort")
   :snort_sig s/Str})

(s/defschema SIOCSpecification
  "An indicator which runs in snort..."
  {:type (s/eq "SIOC")
   :SIOC s/Str})

(s/defschema OpenIOCSpecification
  "An indicator which contains an XML blob of an openIOC indicator.."
  {:type (s/eq "OpenIOC")
   :open_IOC s/Str})

(s/defschema Sighting
  "See http://stixproject.github.io/data-model/1.2/indicator/SightingType/"
  {(s/optional-key :timestamp) c/Time
   (s/optional-key :source) s/Str
   (s/optional-key :reference) c/URI
   (s/optional-key :confidence) v/HighMedLow
   (s/optional-key :description) s/Str
   (s/optional-key :related_judgements) rel/RelatedJudgements})

(s/defschema CompositeIndicatorExpression
  "See http://stixproject.github.io/data-model/1.2/indicator/CompositeIndicatorExpressionType/"
  {:operator (s/enum "and" "or" "not")
   :indicators [rel/IndicatorReference]})

(s/defschema Indicator
  "See http://stixproject.github.io/data-model/1.2/indicator/IndicatorType/"
  (merge
   c/GenericStixIdentifiers
   {:valid_time c/ValidTime
    (s/optional-key :alternate_ids) [s/Str]
    (s/optional-key :version) s/Num
    (s/optional-key :negate) s/Bool ;; Indicates absence of a pattern
    (s/optional-key :type) [v/IndicatorType]
    (s/optional-key :judgements) rel/RelatedJudgements
    (s/optional-key :composite_indicator_expression) CompositeIndicatorExpression
    (s/optional-key :indicated_TTP) rel/RelatedTTPs
    (s/optional-key :likely_impact) s/Str
    (s/optional-key :suggested_COAs) rel/RelatedCOAs
    (s/optional-key :confidence) v/HighMedLow
    (s/optional-key :sightings) [Sighting] ;; simplified
    (s/optional-key :related_indicators) rel/RelatedIndicators
    (s/optional-key :related_campaigns) rel/RelatedCampaigns
    (s/optional-key :related_COAs) rel/RelatedCOAs
    (s/optional-key :kill_chain_phases) [s/Str] ;; simplified
    (s/optional-key :test_mechanisms) [s/Str] ;; simplified

    ;; Extension fields:
    :producer s/Str

    ;; Extension field :specification
    ;; we should use a conditional based on the :type field of the
    ;; specification, and not an either
    (s/optional-key :specifications) [(s/either
                                       JudgementSpecification
                                       ThreatBrainSpecification
                                       SnortSpecification
                                       SIOCSpecification
                                       OpenIOCSpecification)]

    ;; Not provided: handling
    }))

(s/defschema NewIndicator
  (st/merge
   (st/dissoc Indicator
              :id
              :valid_time)
   {(s/optional-key :valid_time) c/ValidTime}))

(s/defschema StoredIndicator
  "A feedback record at rest in the storage service"
  (st/merge Indicator
            {:owner s/Str
             :created c/Time
             :modified c/Time}))

(s/defn realize-indicator :- StoredIndicator
  ([new-indicator :- NewIndicator
    id :- s/Str]
   (realize-indicator new-indicator id nil))
  ([new-indicator :- NewIndicator
    id :- s/Str
    prev-indicator :- (s/maybe StoredIndicator)]
   (let [now (c/timestamp)]
     (assoc new-indicator
            :id id
            :owner "not implemented"
            :created (or (:created prev-indicator) now)
            :modified now
            :valid_time (or (:valid_time prev-indicator)
                            {:start_time (or (get-in new-indicator [:valid_time :start_time])
                                             now)
                             :end_time (or (get-in new-indicator [:valid_time :end_time])
                                           c/default-expire-date)})))))
