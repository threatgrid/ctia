(ns ctia.http.handlers.stix12.indicator.related-indicators
  (:require [ctia.store :as store])
  (:import [org.mitre.stix.common_1
            ConfidenceType
            ControlledVocabularyStringType
            InformationSourceType
            RelatedIndicatorType
            StructuredTextType]

           [org.mitre.stix.indicator_2
            Indicator
            RelatedIndicatorsType]))

(defn related-indicators? [indicator]
  (boolean (not-empty (:related_indicators indicator))))

(defn attach-related-indicators
  [^Indicator xml-indicator indicator xml-indicator-factory]
  (.withRelatedIndicators xml-indicator
   (doto (RelatedIndicatorsType.)
     (.withRelatedIndicators
      (for [{id :indicator_id
             :keys [confidence source relationship]}
            (:related_indicators indicator)

            :let [rel-ind (store/read-indicator @store/indicator-store id)
                  stx-ind (if rel-ind (xml-indicator-factory rel-ind))]
            :when stx-ind]
        (-> (RelatedIndicatorType.)
            (.withIndicator stx-ind)
            (.withConfidence
             (doto (ConfidenceType.)
               (.setValue
                (doto (ControlledVocabularyStringType.)
                  (.setValue confidence)
                  (.setVocabName "HighMediumLowVocab-1.0")))))
            (.withRelationship
             (doto (ControlledVocabularyStringType.)
               (.setValue relationship)
               (.setVocabName "Any")))
            (.withInformationSource
             (doto (InformationSourceType.)
               (.withDescriptions
                [(doto (StructuredTextType.)
                   (.setValue relationship))])))))))))
