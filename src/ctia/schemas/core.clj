(ns ctia.schemas.core
  (:require [ctim.schemas
             [actor :as as]
             [bundle :as bs]
             [campaign :as cs]
             [coa :as coas]
             [common :as cos]
             [data-table :as ds]
             [exploit-target :as es]
             [feedback :as feedbacks]
             [incident :as is]
             [indicator :as ins]
             [judgement :as js]
             [relationships :as rels]
             [sighting :as ss]
             [ttp :as ttps]
             [verdict :as vs]
             [vocabularies :as vocs]]
            [flanders.schema :as fs]
            [schema.core :as s]))

;; actor
(def NewActor (fs/->schema-tree as/NewActor))
(def StoredActor (fs/->schema-tree as/StoredActor))

;; campaign
(def NewCampaign (fs/->schema-tree cs/NewCampaign))
(def StoredCampaign (fs/->schema-tree cs/StoredCampaign))

;; coa
(def NewCOA (fs/->schema-tree coas/NewCOA))
(def StoredCOA (fs/->schema-tree coas/StoredCOA))

;; data-table
(def NewDataTable (-> ds/NewDataTable
                      fs/replace-either-with-any
                      fs/->schema-tree))

(def StoredDataTable (-> ds/StoredDataTable
                         fs/replace-either-with-any
                         fs/->schema-tree))

;; exploit-target
(def NewExploitTarget (fs/->schema-tree es/NewExploitTarget))
(def StoredExploitTarget (fs/->schema-tree es/StoredExploitTarget))

;; sighting
(def NewSighting (fs/->schema-tree ss/NewSighting))
(def StoredSighting (fs/->schema-tree ss/StoredSighting))

;; judgement
(def NewJudgement (fs/->schema-tree js/NewJudgement))
(def StoredJudgement (fs/->schema-tree js/StoredJudgement))

;; verdict
(def Verdict (fs/->schema-tree vs/Verdict))
(def StoredVerdict (fs/->schema-tree vs/StoredVerdict))

;; feedback
(def NewFeedback (fs/->schema-tree feedbacks/NewFeedback))
(def StoredFeedback (fs/->schema-tree feedbacks/StoredFeedback))

;; incident
(def NewIncident (fs/->schema-tree is/NewIncident))
(def StoredIncident (fs/->schema-tree is/StoredIncident))

;; indicator
(def NewIndicator (-> ins/NewIndicator
                      fs/replace-either-with-any
                      fs/->schema-tree))

(def StoredIndicator (-> ins/StoredIndicator
                         fs/replace-either-with-any
                         fs/->schema-tree))

;; ttp
(def NewTTP (fs/->schema-tree ttps/NewTTP))
(def StoredTTP (fs/->schema-tree ttps/StoredTTP))

;; bundle
(def NewBundle (-> bs/NewBundle
                   fs/replace-either-with-any
                   fs/->schema-tree))

(def StoredBundle (-> bs/StoredBundle
                      fs/replace-either-with-any
                      fs/->schema-tree))

;; relationships
(def RelatedIndicator (fs/->schema-tree rels/RelatedIndicator))

;; common
(def Observable (fs/->schema-tree cos/Observable))
(def Reference (fs/->schema-tree cos/Reference))
(def ID (fs/->schema-tree cos/ID))

(s/defschema VersionInfo
  "Version information for a specific instance of CTIA"
  {:base s/Str
   :version s/Str
   :build s/Str
   :beta s/Bool
   :supported_features [s/Str]})

;; vocabularies
(def ObservableTypeIdentifier
  (fs/->schema-tree vocs/ObservableTypeIdentifier))
