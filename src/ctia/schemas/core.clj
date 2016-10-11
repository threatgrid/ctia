(ns ctia.schemas.core
  (:require
   [flanders.schema :as fs]
   [ctim.schemas.actor :as as]
   [ctim.schemas.campaign :as cs]
   [ctim.schemas.coa :as coas]
   [ctim.schemas.data-table :as ds]
   [ctim.schemas.exploit-target :as es]
   [ctim.schemas.sighting :as ss]
   [ctim.schemas.judgement :as js]
   [ctim.schemas.verdict :as vs]
   [ctim.schemas.feedback :as feedbacks]
   [ctim.schemas.incident :as is]
   [ctim.schemas.indicator :as ins]
   [ctim.schemas.ttp :as ttps]
   [ctim.schemas.relationships :as rels]
   [ctim.schemas.common :as cos]
   [ctim.schemas.vocabularies :as vocs]
   [ctim.schemas.bundle :as bs]))

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
(def NewDataTable (fs/->schema-tree ds/NewDataTable))
(def StoredDataTable (fs/->schema-tree ds/StoredDataTable))

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
(def NewIndicator (fs/->schema-tree ins/NewIndicator))
(def StoredIndicator (fs/->schema-tree ins/StoredIndicator))

;; ttp
(def NewTTP (fs/->schema-tree ttps/NewTTP))
(def StoredTTP (fs/->schema-tree ttps/StoredTTP))

;; bundle
(def NewBundle (fs/->schema-tree bs/NewBundle))
(def StoredBundle (fs/->schema-tree bs/StoredBundle))

;; relationships
(def RelatedIndicator (fs/->schema-tree rels/RelatedIndicator))

;; common
(def Observable (fs/->schema-tree cos/Observable))

;; vocabularies
(def ObservableTypeIdentifier (fs/->schema-tree vocs/ObservableTypeIdentifier))
