(ns ctia.schemas.core
  (:require [ctim.schemas
             [actor :as as]
             [campaign :as cs]
             [coa :as coas]
             [common :as cos]
             [data-table :as ds]
             [exploit-target :as es]
             [feedback :as feedbacks]
             [incident :as is]
             [indicator :as ins]
             [judgement :as js]
             [relationship :as rels]
             [sighting :as ss]
             [ttp :as ttps]
             [verdict :as vs]
             [vocabularies :as vocs]]
            [flanders
             [schema :as f-schema]
             [spec :as f-spec]
             [utils :as fu]]
            [schema.core :refer [Str Bool] :as sc]))

(defmacro defschema [name-sym ddl spec-kw-ns]
  `(do
     (sc/defschema ~name-sym (f-schema/->schema ~ddl))
     (f-spec/->spec ~ddl ~spec-kw-ns)))

;; actor

(defschema Actor
  as/Actor
  "actor")

(defschema NewActor
  as/NewActor
  "new-actor")

(defschema StoredActor
  as/StoredActor
  "stored-actor")

;; campaign

(defschema Campaign
  cs/Campaign
  "campaign")

(defschema NewCampaign
  cs/NewCampaign
  "new-campaign")

(defschema StoredCampaign
  cs/StoredCampaign
  "stored-campaign")

;; coa

(defschema COA
  coas/COA
  "coa")

(defschema NewCOA
  coas/NewCOA
  "new-coa")

(defschema StoredCOA
  coas/StoredCOA
  "stored-coa")

;; data-table

(defschema DataTable
  (-> ds/DataTable
      fu/replace-either-with-any)
  "data-table")

(defschema NewDataTable
  (-> ds/NewDataTable
      fu/replace-either-with-any)
  "new-data-table")

(defschema StoredDataTable
  (-> ds/StoredDataTable
      fu/replace-either-with-any)
  "stored-data-table")

;; exploit-target

(defschema ExploitTarget
  es/ExploitTarget
  "exploit-target")

(defschema NewExploitTarget
  es/NewExploitTarget
  "new-exploit-target")

(defschema StoredExploitTarget
  es/StoredExploitTarget
  "stored-exploit-target")

;; sighting

(defschema NewSighting
  ss/NewSighting
  "new-sighting")

(defschema Sighting
  ss/Sighting
  "sighting")

(defschema StoredSighting
  ss/StoredSighting
  "stored-sighting")

;; judgement

(defschema Judgement
  js/Judgement
  "judgement")

(defschema NewJudgement
  js/NewJudgement
  "new-judgement")

(defschema StoredJudgement
  js/StoredJudgement
  "stored-judgement")

;; verdict

(defschema Verdict
  vs/Verdict
  "verdict")

(defschema StoredVerdict
  vs/StoredVerdict
  "stored-verdict")

;; feedback

(defschema Feedback
  feedbacks/Feedback
  "feedback")

(defschema NewFeedback
  feedbacks/NewFeedback
  "new-feedback")

(defschema StoredFeedback
  feedbacks/StoredFeedback
  "stored-feedback")

;; incident

(defschema Incident
  is/Incident
  "incident")

(defschema NewIncident
  is/NewIncident
  "new-incident")

(defschema StoredIncident
  is/StoredIncident
  "stored-incident")

;; indicator

(sc/defschema Indicator
  (f-schema/->schema
   (-> ins/Indicator
       fu/replace-either-with-any)))

(f-spec/->spec ins/Indicator "indicator")

(sc/defschema NewIndicator
  (f-schema/->schema
   (-> ins/NewIndicator
       fu/replace-either-with-any)))

(f-spec/->spec ins/NewIndicator "new-indicator")

(sc/defschema StoredIndicator
  (f-schema/->schema
   (-> ins/StoredIndicator
       fu/replace-either-with-any)))

(f-spec/->spec ins/StoredIndicator "stored-indicator")

;; relationship

(defschema Relationship
  rels/Relationship
  "relationship")

(defschema NewRelationship
  rels/NewRelationship
  "new-relationship")

(defschema StoredRelationship
  rels/StoredRelationship
  "stored-relationship")

;; ttp

(defschema TTP
  ttps/TTP
  "ttp")

(defschema NewTTP
  ttps/NewTTP
  "new-ttp")

(defschema StoredTTP
  ttps/StoredTTP
  "stored-ttp")

;; common

(defschema Observable
  cos/Observable
  "common.observable")

(defschema Reference
  cos/Reference
  "common.reference")

(defschema ID
  cos/ID
  "common.id")

(sc/defschema VersionInfo
  "Version information for a specific instance of CTIA"
  {:base Str
   :version Str
   :build Str
   :beta Bool
   :supported_features [Str]})

;; vocabularies

(defschema ObservableTypeIdentifier
  vocs/ObservableTypeIdentifier
  "vocab.observable-type-id")

(def stored-schema-lookup
  {:actor StoredActor
   :campaign StoredCampaign
   :coa StoredCOA
   :exploit-target StoredExploitTarget
   :feedback StoredFeedback
   :incident StoredIncident
   :indicator StoredIndicator
   :judgement StoredJudgement
   :relationship StoredRelationship
   :sighting StoredSighting
   :ttp StoredTTP
   :verdict StoredVerdict})
