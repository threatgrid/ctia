(ns cia.schemas.relationships
  (:require [cia.schemas.common :as c]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(def ActorReference c/Reference)
(def CampaignReference c/Reference)
(def COAReference c/Reference)
(def ExploitTargetReference c/Reference)
(def FeedbackReference c/Reference)
(def IncidentReference c/Reference)
(def IndicatorReference c/Reference)
(def JudgementReference c/Reference)
(def TTPReference c/Reference)
(def VerdictReference c/Reference)

(defn enriched-ref [reference-map]
  (st/merge
   {(s/optional-key :confidence) v/HighMedLow
    (s/optional-key :source) c/Source
    (s/optional-key :relationship) s/Str}
   reference-map))

(defn relation [string-type map-type]
  (s/conditional
   map? map-type
   :else string-type))

(s/defschema RelatedIndicators
  [(relation IndicatorReference
             (enriched-ref {:indicator IndicatorReference}))])

(s/defschema RelatedActors
  [(relation ActorReference
             (enriched-ref {:actor ActorReference}))])

(s/defschema RelatedCampaigns
  [(relation CampaignReference
             (enriched-ref {:campaign CampaignReference}))])

(s/defschema RelatedCOAs
  [(relation COAReference
             (enriched-ref {:COA COAReference}))])

(s/defschema RelatedExploitTargets
  [(relation ExploitTargetReference
             (enriched-ref {:exploit_target ExploitTargetReference}))])

(s/defschema RelatedIncidents
  [(relation IncidentReference
             (enriched-ref {:incident IncidentReference}))])

(s/defschema RelatedIndicators
  [(relation IndicatorReference
             (enriched-ref {:indicator IndicatorReference}))])

(s/defschema RelatedJudgements
  [(relation JudgementReference
             (enriched-ref {:judgement JudgementReference}))])

(s/defschema RelatedTTP
  (enriched-ref {:ttp TTPReference}))

(s/defschema RelatedTTPs
  [(relation TTPReference
             RelatedTTP)])
