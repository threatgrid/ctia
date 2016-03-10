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
    (s/optional-key :source) s/Str
    (s/optional-key :relationship) s/Str}
   reference-map))

(s/defschema RelatedIndicators
  [(enriched-ref {:indicator_id IndicatorReference})])

(s/defschema RelatedActors
  [(enriched-ref {:actor_id ActorReference})])

(s/defschema RelatedCampaigns
  [(enriched-ref {:campaign_id CampaignReference})])

(s/defschema RelatedCOAs
  [(enriched-ref {:COA_id COAReference})])

(s/defschema RelatedExploitTargets
  [(enriched-ref {:exploit_target_id ExploitTargetReference})])

(s/defschema RelatedIncidents
  [(enriched-ref {:incident_id IncidentReference})])

(s/defschema RelatedIndicators
  [(enriched-ref {:indicator_id IndicatorReference})])

(s/defschema RelatedJudgements
  [(enriched-ref {:judgement_id JudgementReference})])

(s/defschema RelatedTTP
  (enriched-ref {:ttp_id TTPReference}))

(s/defschema RelatedTTPs
  [(enriched-ref {:ttp_id TTPReference})])

;; (s/defschema RelatedTTPs
;;   [(relation TTPReference
;;              RelatedTTP)])
