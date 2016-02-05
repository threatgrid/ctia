(ns cia.schemas.indicator
  (:require [cia.schemas.common :refer :all]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [cia.schemas.common :as c]))

(s/defschema ValidTime
  "See http://stixproject.github.io/data-model/1.2/indicator/ValidTimeType/"
  {(s/optional-key :start_time) c/Time
   (s/optional-key :end_time) c/Time})

(s/defschema JudgementSpecification
  "An indicator based on a list of judgements.  If any of the
  Observables in it's judgements are encountered, than it may be
  matches against.  If there are any required judgements, they all
  must be matched in order for the indicator to be considered a
  match."
  {:type (s/eq "Judgement")
   :judgements [c/Reference] ;; TODO - name these references
   :required_judgements [c/Reference]})

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
   (s/optional-key :source) c/Source
   (s/optional-key :reference) c/URI
   (s/optional-key :confidence) v/HighMedLow
   (s/optional-key :description) [s/Str]
   (s/optional-key :related_observables) rel/RelatedObservables})

(s/defschema Sightings
  "See http://stixproject.github.io/data-model/1.2/indicator/SightingsType/"
  {(s/optional-key :sightings_count) s/Int
   :sightings [Sighting]})

(s/defschema Indicator
  "See http://stixproject.github.io/data-model/1.2/indicator/IndicatorType/"
  (merge
   c/GenericStixIdentifiers
   {(s/optional-key :alternate_ids) [s/Str]
    (s/optional-key :version) s/Num
    (s/optional-key :negate) s/Bool ;; Indicates absence of a pattern
    (s/optional-key :type) [v/IndicatorType]
    (s/optional-key :valid_time_position) ValidTime
    (s/optional-key :observable) rel/ObservableReference
    (s/optional-key :composite_indicator_expression) rel/CompositeIndicatorExpression
    (s/optional-key :indicated_TTP) rel/RelatedTTP
    (s/optional-key :likely_impact) s/Str
    (s/optional-key :suggested_COAs) rel/SuggestedCOAs
    (s/optional-key :confidence) v/HighMedLow
    (s/optional-key :sightings) Sightings
    (s/optional-key :related_indicators) rel/RelatedIndicators
    (s/optional-key :related_campaigns) rel/RelatedCampaigns
    (s/optional-key :related_COAs) rel/RelatedCOAs
    (s/optional-key :kill_chain_phases) [s/Str] ;; simplified
    (s/optional-key :test_mechanisms) [s/Str] ;; simplified

    ;; Extension fields:
    (s/optional-key :expires) c/Time
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

(def NewIndicator
  (dissoc Indicator :id :idref))

(def StoredIndicator
  "A feedback record at rest in the storage service"
  (merge Indicator
         {:owner s/Str
          :created c/Time
          :timestamp c/Time}))
