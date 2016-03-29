(ns ctia.web.handlers.stix12.indicator.likely-impact
  (:import [org.mitre.stix.common_1
            StatementType
            StructuredTextType]

           [org.mitre.stix.indicator_2
            Indicator]))

(defn likely-impact? [indicator]
  (boolean (:likely_impact indicator)))

(defn attach-likely-impact [^Indicator xml-indicator indicator]
  (.withLikelyImpact xml-indicator
   (-> (StatementType.)
       (.withDescriptions
        [(.withValue (StructuredTextType.) (:likely_impact indicator))]))))
