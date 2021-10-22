(ns ctia.schemas.graphql.refs
  (:require [ctia.schemas.graphql.helpers :as g]))

;;---- Attack Pattern
(def attack-pattern-type-name "AttackPattern")
(def AttackPatternRef
  (g/new-ref attack-pattern-type-name))

;;---- Incident
(def incident-type-name "Incident")
(def IncidentRef
  (g/new-ref incident-type-name))

;;---- Indicator
(def indicator-type-name "Indicator")
(def IndicatorRef
  (g/new-ref indicator-type-name))

;;---- Judgement
(def judgement-type-name "Judgement")
(def JudgementRef
  (g/new-ref judgement-type-name))

;;---- Malware
(def malware-type-name "Malware")
(def MalwareRef
  (g/new-ref malware-type-name))

;;---- Observable
(def observable-type-name "Observable")
(def ObservableTypeRef (g/new-ref observable-type-name))

;;---- Relationship
;; TODO: remove unused vars
;; (def relationship-connection-type-name "RelationshipConnection")
;; (def relationship-type-name "Relationship")
;; (def RelationshipRef
;;     (g/new-ref relationship-type-name))

(def related-judgement-type-name "RelatedJudgement")

;;---- Sighting
(def sighting-type-name "Sighting")
(def SightingRef
  (g/new-ref sighting-type-name))

;;---- Verdict
(def verdict-type-name "Verdict")
(def VerdictRef
  (g/new-ref verdict-type-name))

;;---- Vulnerability
(def vulnerability-type-name "Vulnerability")
(def VulnerabilityRef
  (g/new-ref vulnerability-type-name))

;;---- Weakness
(def weakness-type-name "Weakness")
(def WeaknessRef
  (g/new-ref weakness-type-name))

;;---- Tool
(def tool-type-name "Tool")
(def ToolRef
  (g/new-ref tool-type-name))
