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
             [schema :as fs]
             [utils :as fu]]
            [schema.core :refer [defschema Str Bool]]))

;; actor
(defschema NewActor (fs/->schema-tree as/NewActor))
(defschema StoredActor (fs/->schema-tree as/StoredActor))

;; campaign
(defschema NewCampaign (fs/->schema-tree cs/NewCampaign))
(defschema StoredCampaign (fs/->schema-tree cs/StoredCampaign))

;; coa
(defschema NewCOA (fs/->schema-tree coas/NewCOA))
(defschema StoredCOA (fs/->schema-tree coas/StoredCOA))

;; data-table
(defschema NewDataTable (-> ds/NewDataTable
                            fu/replace-either-with-any
                            fs/->schema-tree))

(defschema StoredDataTable (-> ds/StoredDataTable
                               fu/replace-either-with-any
                               fs/->schema-tree))

;; exploit-target
(defschema NewExploitTarget (fs/->schema-tree es/NewExploitTarget))
(defschema StoredExploitTarget (fs/->schema-tree es/StoredExploitTarget))

;; sighting
(defschema NewSighting (fs/->schema-tree ss/NewSighting))
(defschema StoredSighting (fs/->schema-tree ss/StoredSighting))

;; judgement
(defschema NewJudgement (fs/->schema-tree js/NewJudgement))
(defschema StoredJudgement (fs/->schema-tree js/StoredJudgement))

;; verdict
(defschema Verdict (fs/->schema-tree vs/Verdict))
(defschema StoredVerdict (fs/->schema-tree vs/StoredVerdict))

;; feedback
(defschema NewFeedback (fs/->schema-tree feedbacks/NewFeedback))
(defschema StoredFeedback (fs/->schema-tree feedbacks/StoredFeedback))

;; incident
(defschema NewIncident (fs/->schema-tree is/NewIncident))
(defschema StoredIncident (fs/->schema-tree is/StoredIncident))

;; indicator
(defschema NewIndicator (-> ins/NewIndicator
                            fu/replace-either-with-any
                            fs/->schema-tree))

(defschema StoredIndicator (-> ins/StoredIndicator
                               fu/replace-either-with-any
                               fs/->schema-tree))



;; ttp
(defschema NewTTP (fs/->schema-tree ttps/NewTTP))
(defschema StoredTTP (fs/->schema-tree ttps/StoredTTP))

;; relationships
(defschema NewRelationship (fs/->schema-tree rels/NewRelationship))
(defschema StoredRelationship (fs/->schema-tree rels/StoredRelationship))

(defschema RelatedIndicator (fs/->schema-tree rels/RelatedIndicator))

;; common
(defschema Observable (fs/->schema-tree cos/Observable))
(defschema Reference (fs/->schema-tree cos/Reference))
(defschema ID (fs/->schema-tree cos/ID))

(defschema VersionInfo
  "Version information for a specific instance of CTIA"
  {:base Str
   :version Str
   :build Str
   :beta Bool
   :supported_features [Str]})

;; vocabularies
(defschema ObservableTypeIdentifier
  (fs/->schema-tree vocs/ObservableTypeIdentifier))

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
