(ns ctia.schemas.graphql.refs
  (:require [ctia.schemas.graphql.helpers :as g]))

;;---- Indicator
(def indicator-type-name "Indicator")
(def IndicatorRef
  (g/new-ref indicator-type-name))

;;---- Judgement
(def judgement-type-name "Judgement")
(def JudgementRef
  (g/new-ref judgement-type-name))

;;---- Observable
(def observable-type-name "Observable")
(def ObservableTypeRef (g/new-ref observable-type-name))

;;---- Relationship
(def relationship-connection-type-name "RelationshipConnection")
(def relationship-type-name "Relationship")
(def RelationshipRef
  (g/new-ref relationship-type-name))

(def related-judgement-type-name "RelatedJudgement")

;;---- Sighting
(def sighting-type-name "Sighting")
(def SightingRef
  (g/new-ref sighting-type-name))

;;---- Verdict
(def verdict-type-name "Verdict")
(def VerdictRef
  (g/new-ref verdict-type-name))

