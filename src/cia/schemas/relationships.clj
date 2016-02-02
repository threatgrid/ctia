(ns cia.schemas.relationships
  (:require [cia.schemas.common :as c]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]))

(def ActorReference c/Reference)
(def CampaignReference c/Reference)
(def COAReference c/Reference)
(def ExploitTargetReference c/Reference)
(def IncidentReference c/Reference)
(def IndicatorReference c/Reference)
(def ObservableReference c/Reference)
(def TTPReference c/Reference)

(s/defschema RelatedWrapper
  "For merging into RelatedFoo style structures, where Foo is the structure type"
  {(s/optional-key :confidence) v/HighMedLow
   (s/optional-key :source) c/Source
   (s/optional-key :relationship) s/Str})

;; indicator

(s/defschema RelatedIndicator
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedIndicatorType/"
  (merge
   RelatedWrapper
   {:indicator IndicatorReference}))

(s/defschema RelatedIndicators
  "See http://stixproject.github.io/data-model/1.2/indicator/RelatedIndicatorsType/"
  (merge
   c/ScopeWrapper
   {:related_indicators [RelatedIndicator]}))

;; actor

(s/defschema RelatedActor
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedThreatActorType/"
  (merge
   RelatedWrapper
   {:actor [ActorReference]}))

(s/defschema AssociatedActors
  "See http://stixproject.github.io/data-model/1.2/ta/AssociatedActorsType/"
  (merge
   c/ScopeWrapper
   {:associated_actors [RelatedActor]}))

(s/defschema AttributedActors
  "See http://stixproject.github.io/data-model/1.2/incident/AttributedThreatActorsType/"
  (merge
   c/ScopeWrapper
   {:attributed_actors [RelatedActor]}))

;; campaign

(s/defschema RelatedCampaign
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedCampaignType/"
  (merge
   RelatedWrapper
   {:campaigns CampaignReference}))

(s/defschema AssociatedCampaigns
  "See http://stixproject.github.io/data-model/1.2/ta/AssociatedCampaignsType/"
  (merge
   c/ScopeWrapper
   {:associated_campaigns [RelatedCampaign]}))

(s/defschema RelatedCampaigns
  "See http://stixproject.github.io/data-model/1.2/indicator/RelatedCampaignReferencesType/"
  (merge
   c/ScopeWrapper
   {:related_campaigns [RelatedCampaign]}))

;; coa

(s/defschema RelatedCOA
  "See http://stixproject.github.data-model/1.2/stixCommon/RelatedCourseOfActionType/"
  (merge
   RelatedWrapper
   {(s/optional-key :COA) COAReference}))

(s/defschema PotentialCOAs
  "See http://stixproject.github.io/data-model/1.2/et/PotentialCOAsType/"
  (merge
   c/ScopeWrapper
   {:potential_COAs [RelatedCOA]}))

(s/defschema RelatedCOAs
  "See http://stixproject.github.io/data-model/1.2/coa/RelatedCOAsType/"
  (merge
   c/ScopeWrapper
   {:related_COAs [RelatedCOA]}))

(s/defschema SuggestedCOAs
  "See http://stixproject.github.io/data-model/1.2/indicator/SuggestedCOAsType/"
  (merge
   c/ScopeWrapper
   {:suggested_COAs [RelatedCOA]}))

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

(s/defschema RelatedExploitTargets
  "See http://stixproject.github.io/data-model/1.2/ttp/ExploitTargetsType/"
  (merge
   c/ScopeWrapper
   {:exploit_targets [RelatedExploitTarget]}))

;; incident

(s/defschema RelatedIncident
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedIncidentType/"
  (merge
   RelatedWrapper
   {:incident IncidentReference}))

(s/defschema RelatedIncidents
  "See http://stixproject.github.io/data-model/1.2/campaign/RelatedIncidentsType/"
  (merge
   c/ScopeWrapper
   {:related_incidents [RelatedIncident]}))

;; indicator

(s/defschema CompositeIndicatorExpression
  "See http://stixproject.github.io/data-model/1.2/indicator/CompositeIndicatorExpressionType/"
  {:operator (s/either "and" "or" "not")
   :indicators [IndicatorReference]})

;; ttp

(s/defschema RelatedTTP
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedTTPType/"
  (merge
   RelatedWrapper
   {:TTP TTPReference}))

(s/defschema RelatedTTPs
  "See http://stixproject.github.io/data-model/1.2/ttp/RelatedTTPsType/"
  (merge
   c/ScopeWrapper
   {:related_TTPs [RelatedTTP]}))

(s/defschema ObservedTTPs
  "See http://stixproject.github.io/data-model/1.2/ta/ObservedTTPsType/"
  (merge
   c/ScopeWrapper
   {:observed_TTPs [RelatedTTP]}))

(s/defschema LeveragedTTPs
  "See http://stixproject.github.io/data-model/1.2/incident/LeveragedTTPsType/"
  (merge
   c/ScopeWrapper
   {:levereged_TTPs [RelatedTTP]}))

;; observable

(s/defschema RelatedObservable
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedObservableType/"
  (merge
   RelatedWrapper
   {:observable [ObservableReference]}))

(s/defschema RelatedObservables
  "See http://stixproject.github.io/data-model/1.2/indicator/RelatedObservablesType/"
  (merge
   c/ScopeWrapper
   {:related_observable [RelatedObservable]}))
