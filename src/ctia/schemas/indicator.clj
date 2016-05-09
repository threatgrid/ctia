(ns ctia.schemas.indicator
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st]))

(s/defschema JudgementSpecification
  "An indicator based on a list of judgements.  If any of the
  Observables in it's judgements are encountered, than it may be
  matches against.  If there are any required judgements, they all
  must be matched in order for the indicator to be considered a
  match."
  {:type (s/enum "Judgement")
   :judgements [rel/JudgementReference]
   :required_judgements rel/RelatedJudgements})

(s/defschema ThreatBrainSpecification
  "An indicator which runs in threatbrain..."
  {:type (s/enum "ThreatBrain")
   (s/optional-key :query) s/Str
   :variables [s/Str] })

(s/defschema SnortSpecification
  "An indicator which runs in snort..."
  {:type (s/enum "Snort")
   :snort_sig s/Str})

(s/defschema SIOCSpecification
  "An indicator which runs in snort..."
  {:type (s/enum "SIOC")
   :SIOC s/Str})

(s/defschema OpenIOCSpecification
  "An indicator which contains an XML blob of an openIOC indicator.."
  {:type (s/enum "OpenIOC")
   :open_IOC s/Str})

(s/defschema CompositeIndicatorExpression
  "See http://stixproject.github.io/data-model/1.2/indicator/CompositeIndicatorExpressionType/"
  {:operator (s/enum "and" "or" "not")
   :indicator_ids [rel/IndicatorReference]})

(s/defschema Indicator
  "See http://stixproject.github.io/data-model/1.2/indicator/IndicatorType/"
  (st/merge
   c/GenericStixIdentifiers
   {:valid_time c/ValidTime}
   (st/optional-keys
    {:alternate_ids (describe [s/Str] "alternative identifier (or alias)")
     :version (describe s/Str "schema version for this content")
     :negate (describe s/Bool "specifies the absence of the pattern")
     :indicator_type (describe [v/IndicatorType]
                               "Specifies the type or types for this Indicator")
     :tags (describe [s/Str] "Descriptors for this indicator")
     :observable (describe c/Observable
                           "a relevant cyber observable for this Indicator")
     :judgements (describe rel/RelatedJudgements
                           "related Judgements for this Indicator")
     :composite_indicator_expression CompositeIndicatorExpression
     :indicated_TTP (describe rel/RelatedTTPs
                              "the relevant TTP indicated by this Indicator")
     :likely_impact (describe
                     s/Str
                     (str "likely potential impact within the relevant context"
                          " if this Indicator were to occur"))
     :suggested_COAs (describe rel/RelatedCOAs "suggested Courses of Action")
     :confidence (describe
                  v/HighMedLow
                  "level of confidence held in the accuracy of this Indicator")
     :related_indicators (describe
                          rel/RelatedIndicators
                          (str "relationship between the enclosing indicator and"
                               " a disparate indicator"))
     :related_campaigns (describe rel/RelatedCampaigns
                                  "references to related campaigns")
     :related_COAs (describe
                    rel/RelatedCOAs
                    "related Courses of Actions for this cyber threat Indicator")
     :kill_chain_phases (describe
                         [s/Str]
                         "relevant kill chain phases indicated by this Indicator") ;; simplified
     :test_mechanisms (describe
                       [s/Str]
                       (str "Test Mechanisms effective at identifying the cyber"
                            " Observables specified in this"
                            " cyber threat Indicator")) ;; simplified
     })
   ;; Extension fields:
   {:producer s/Str ;; TODO - Document what is supposed to be in this field!
    (s/optional-key :specifications) [(s/conditional
                                       #(= "Judgement" (:type %)) JudgementSpecification
                                       #(= "ThreatBrain" (:type %)) ThreatBrainSpecification
                                       #(= "Snort" (:type %)) SnortSpecification
                                       #(= "SIOC" (:type %)) SIOCSpecification
                                       #(= "OpenIOC" (:type %)) OpenIOCSpecification)]

    ;; Not provided: handling
    }))

(s/defschema Type
  (s/enum "indicator"))

(s/defschema NewIndicator
  (st/merge
   (st/dissoc Indicator
              :id
              :valid_time)
   {(s/optional-key :valid_time) c/ValidTime
    (s/optional-key :type) Type}))

(s/defschema StoredIndicator
  "An indicator as stored in the data store"
  (c/stored-schema "indicator" Indicator))

(def realize-indicator
  (c/default-realize-fn "indicator" NewIndicator StoredIndicator))

(defn generalize-indicator
  "Strips off realized fields"
  [indicator]
  (dissoc indicator
          :id
          :type
          :created
          :modified
          :owner))
