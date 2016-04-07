(ns ctia.http.handlers.stix12.indicator.composite-indicator-expression
  (:require [clojure.string :as str]
            [ctia.store :as store])
  (:import [org.mitre.stix.indicator_2
            CompositeIndicatorExpressionType
            Indicator
            OperatorTypeEnum]))

(defn composite-indicator-expression? [indicator]
  (boolean (:composite_indicator_expression indicator)))

(defn attach-composite-indicator-expression
  [^Indicator xml-indicator indicator xml-indicator-factory]
  (.withCompositeIndicatorExpression xml-indicator
   (let [{operator :operator
          indicator_ids :indicators}
         (:composite_indicator_expression indicator)]
     (CompositeIndicatorExpressionType.
      (->> indicator_ids
           (map #(store/read-indicator @store/indicator-store %))
           (remove nil?)
           (map xml-indicator-factory))
      (OperatorTypeEnum/fromValue (str/upper-case operator))))))
