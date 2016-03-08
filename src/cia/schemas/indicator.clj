(ns cia.schemas.indicator
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
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
    (s/optional-key :alternate_ids) (describe [s/Str] "alternative identifier (or alias)")
    (s/optional-key :version) (describe s/Num "schema version for this content")
    (s/optional-key :negate) (describe s/Bool "specifies the absence of the pattern")
    (s/optional-key :type) (describe [v/IndicatorType] "Specifies the type or types for this Indicator")
    (s/optional-key :observable) (describe c/Observable "a relevant cyber observable for this Indicator")
    (s/optional-key :judgements) (describe rel/RelatedJudgements "related Judgements for this Indicator")
    (s/optional-key :composite_indicator_expression) CompositeIndicatorExpression
    (s/optional-key :indicated_TTP) (describe rel/RelatedTTPs "the relevant TTP indicated by this Indicator")
    (s/optional-key :likely_impact) (describe s/Str "likely potential impact within the relevant context if this Indicator were to occur")
    (s/optional-key :suggested_COAs) (describe rel/RelatedCOAs "suggested Courses of Action")
    (s/optional-key :confidence) (describe v/HighMedLow "level of confidence held in the accuracy of this Indicator")
    (s/optional-key :sightings) (describe [Sighting] "a set of sighting reports")
    (s/optional-key :related_indicators) (describe rel/RelatedIndicators "relationship between the enclosing indicator and a disparate indicator")
    (s/optional-key :related_campaigns) (describe rel/RelatedCampaigns "references to related campaigns")
    (s/optional-key :related_COAs) (describe rel/RelatedCOAs "related Courses of Actions for this cyber threat Indicator")
    (s/optional-key :kill_chain_phases) (describe [s/Str] "relevant kill chain phases indicated by this Indicator") ;; simplified
    (s/optional-key :test_mechanisms) (describe [s/Str] "Test Mechanisms effective at identifying the cyber Observables specified in this cyber threat Indicator") ;; simplified

    ;; Extension fields:
    :producer s/Str
    (s/optional-key :specifications) [(s/conditional
                                       #(= "Judgement" (:type %)) JudgementSpecification
                                       #(= "ThreatBrain" (:type %)) ThreatBrainSpecification
                                       #(= "Snort" (:type %)) SnortSpecification
                                       #(= "SIOC" (:type %)) SIOCSpecification
                                       #(= "OpenIOC" (:type %)) OpenIOCSpecification)]

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
    id :- s/Str
    login :- s/Str]
   (realize-indicator new-indicator id login nil))
  ([new-indicator :- NewIndicator
    id :- s/Str
    login :- s/Str
    prev-indicator :- (s/maybe StoredIndicator)]
   (let [now (c/timestamp)]
     (assoc new-indicator
            :id id
            :owner login
            :created (or (:created prev-indicator) now)
            :modified now
            :valid_time (or (:valid_time prev-indicator)
                            {:start_time (or (get-in new-indicator [:valid_time :start_time])
                                             now)
                             :end_time (or (get-in new-indicator [:valid_time :end_time])
                                           c/default-expire-date)})))))
