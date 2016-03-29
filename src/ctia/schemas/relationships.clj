(ns ctia.schemas.relationships
  (:require [ctia.schemas.common :as c]
            [ctia.schemas.vocabularies :as v]
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
(def SightingReference c/Reference)
(def TTPReference c/Reference)
(def VerdictReference c/Reference)

(defn enriched-ref [reference-map]
  (st/merge
   {(s/optional-key :confidence) v/HighMedLow
    (s/optional-key :source) s/Str
    (s/optional-key :relationship) s/Str}
   reference-map))

(s/defschema RelatedIndicator
  (enriched-ref {:type (s/eq "indicator")
                 :indicator_id IndicatorReference}))

(s/defschema RelatedIndicators
  [RelatedIndicator])

(s/defschema RelatedActors
  [(enriched-ref {:type (s/eq "actor")
                  :actor_id ActorReference})])

(s/defschema RelatedCampaigns
  [(enriched-ref {:type (s/eq "campaign")
                  :campaign_id CampaignReference})])

(s/defschema RelatedCOAs
  [(enriched-ref {:type (s/eq "COA")
                  :COA_id COAReference})])

(s/defschema RelatedExploitTargets
  [(enriched-ref {:type (s/eq "exploit-target")
                  :exploit_target_id ExploitTargetReference})])

(s/defschema RelatedIncidents
  [(enriched-ref {:type (s/eq "incident")
                  :incident_id IncidentReference})])

(s/defschema RelatedIndicator
  (enriched-ref {:type (s/eq "indicator")
                 :indicator_id IndicatorReference}))

(s/defschema RelatedIndicators
  [RelatedIndicator])

(s/defschema RelatedJudgement
  (enriched-ref {:type (s/eq "judgement")
                 :judgement_id JudgementReference}))

(s/defschema RelatedJudgements
  [RelatedJudgement])

(s/defschema RelatedSightings
  [(enriched-ref {:type (s/eq "sighting")
                  :sighting_id SightingReference})])

(s/defschema RelatedTTP
  (enriched-ref {:type (s/eq "ttp")
                 :ttp_id TTPReference}))

(s/defschema RelatedTTPs
  [RelatedTTP])
