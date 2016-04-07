(ns ctia.http.handlers.stix12.indicator.type
  (:import [org.mitre.stix.common_1
            ControlledVocabularyStringType]

           [org.mitre.stix.indicator_2
            Indicator]))

(defn type? [indicator]
  (boolean (:type indicator)))

(defn attach-type [^Indicator xml-indicator indicator]
  (.withTypes xml-indicator
   (map (fn [value]
          (doto (ControlledVocabularyStringType.)
            (.setValue value)
            (.setVocabName "IndicatorTypeVocab-1.1")))
        (:type indicator))))
