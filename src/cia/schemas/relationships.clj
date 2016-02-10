(ns cia.schemas.relationships
  (:require [cia.schemas.common :as c]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]))

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

(s/defschema RelatedWrapper
  "For merging into RelatedFoo style structures, where Foo is the structure type"
  {(s/optional-key :confidence) v/HighMedLow
   (s/optional-key :source) c/Source
   (s/optional-key :relationship) s/Str})

(defmacro defrel
  "Create the common scoped relationship structure in STIX, but also allow it be
   replaced with a simple vector of references"
  ([name reference merge-map]
   `(defrel ~name "" ~reference ~merge-map))
  ([name doc reference merge-map]
   `(s/defschema ~name
      ~doc
      (s/conditional
       map? (merge
             c/ScopeWrapper
             ~merge-map)
       :else [~reference]))))

;; indicator

(s/defschema RelatedIndicator
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedIndicatorType/"
  (merge
   RelatedWrapper
   {:indicator IndicatorReference}))

(defrel RelatedIndicators
  "See http://stixproject.github.io/data-model/1.2/indicator/RelatedIndicatorsType/"
  IndicatorReference {:related_indicators [RelatedIndicator]})

;; actor

(s/defschema RelatedActor
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedThreatActorType/"
  (merge
   RelatedWrapper
   {:actor [ActorReference]}))

(defrel AssociatedActors
  "See http://stixproject.github.io/data-model/1.2/ta/AssociatedActorsType/"
  ActorReference {:associated_actors [RelatedActor]})

(defrel AttributedActors
  "See http://stixproject.github.io/data-model/1.2/incident/AttributedThreatActorsType/"
  ActorReference {:attributed_actors [RelatedActor]})

;; campaign

(s/defschema RelatedCampaign
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedCampaignType/"
  (merge
   RelatedWrapper
   {:campaign CampaignReference}))

(defrel AssociatedCampaigns
  "See http://stixproject.github.io/data-model/1.2/ta/AssociatedCampaignsType/"
  CampaignReference {:associated_campaigns [RelatedCampaign]})

(defrel RelatedCampaigns
  "See http://stixproject.github.io/data-model/1.2/indicator/RelatedCampaignReferencesType/"
  CampaignReference {:related_campaigns [RelatedCampaign]})

;; coa

(s/defschema RelatedCOA
  "See http://stixproject.github.data-model/1.2/stixCommon/RelatedCourseOfActionType/"
  (merge
   RelatedWrapper
   {(s/optional-key :COA) COAReference}))

(defrel PotentialCOAs
  "See http://stixproject.github.io/data-model/1.2/et/PotentialCOAsType/"
  COAReference {:potential_COAs [RelatedCOA]})

(defrel RelatedCOAs
  "See http://stixproject.github.io/data-model/1.2/coa/RelatedCOAsType/"
  COAReference {:related_COAs [RelatedCOA]})

(defrel SuggestedCOAs
  "See http://stixproject.github.io/data-model/1.2/indicator/SuggestedCOAsType/"
  COAReference {:suggested_COAs [RelatedCOA]})

(s/defschema COARequested
  "See http://stixproject.github.io/data-model/1.2/incident/COARequestedType/
   and http://stixproject.github.io/data-model/1.2/incident/COATakenType/"
  {(s/optional-key :time) c/Time
   (s/optional-key :contributors) [c/Contributor]
   :COA COAReference})

;; exploit-target

(s/defschema RelatedExploitTarget
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedExploitTargetType/"
  (merge
   RelatedWrapper
   {:exploit_target ExploitTargetReference}))

(defrel RelatedExploitTargets
  "See http://stixproject.github.io/data-model/1.2/ttp/ExploitTargetsType/"
  ExploitTargetReference {:exploit_targets [RelatedExploitTarget]})

;; incident

(s/defschema RelatedIncident
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedIncidentType/"
  (merge
   RelatedWrapper
   {:incident IncidentReference}))

(defrel RelatedIncidents
  "See http://stixproject.github.io/data-model/1.2/campaign/RelatedIncidentsType/"
  IncidentReference {:related_incidents [RelatedIncident]})

;; indicator

(s/defschema CompositeIndicatorExpression
  "See http://stixproject.github.io/data-model/1.2/indicator/CompositeIndicatorExpressionType/"
  {:operator (s/enum "and" "or" "not")
   :indicators [IndicatorReference]})

;; ttp

(s/defschema RelatedTTP
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedTTPType/"
  (merge
   RelatedWrapper
   {:TTP TTPReference}))

(defrel RelatedTTPs
  "See http://stixproject.github.io/data-model/1.2/ttp/RelatedTTPsType/"
  TTPReference {:related_TTPs [RelatedTTP]})

(defrel ObservedTTPs
  "See http://stixproject.github.io/data-model/1.2/ta/ObservedTTPsType/"
  TTPReference {:observed_TTPs [RelatedTTP]})

(defrel LeveragedTTPs
  "See http://stixproject.github.io/data-model/1.2/incident/LeveragedTTPsType/"
  TTPReference {:levereged_TTPs [RelatedTTP]})
