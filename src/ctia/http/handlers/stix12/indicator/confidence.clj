(ns ctia.http.handlers.stix12.indicator.confidence
  (:import [org.mitre.stix.common_1
            ConfidenceType
            ControlledVocabularyStringType]

           [org.mitre.stix.indicator_2
            Indicator]))

(defn confidence? [indicator]
  (boolean (:confidence indicator)))

(defn attach-confidence [^Indicator xml-indicator indicator]
  (.withConfidence xml-indicator
   (doto (ConfidenceType.)
     (.setValue
      (doto (ControlledVocabularyStringType.)
        (.setValue (:confidence indicator))
        (.setVocabName "HighMediumLowVocab-1.0"))))))
