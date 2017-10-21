(ns ctia.schemas.core
  (:require [ctim.schemas
             [actor :as as]
             [attack-pattern :as attack]
             [campaign :as cs]
             [coa :as coas]
             [common :as cos]
             [data-table :as ds]
             [exploit-target :as es]
             [feedback :as feedbacks]
             [incident :as is]
             [indicator :as ins]
             [judgement :as js]
             [malware :as malware]
             [relationship :as rels]
             [sighting :as ss]
             [tool :as tool]
             [verdict :as vs]
             [vocabularies :as vocs]]
            [flanders
             [core :as f]
             [schema :as f-schema]
             [spec :as f-spec]
             [utils :as fu]]
            [schema.core :refer [Str Bool] :as sc]
            [schema-tools.core :as st]))



(sc/defschema ACLEntity
  (st/optional-keys
   {:authorized_users [Str]
    :authorized_groups [Str]}))

(sc/defschema ACLStoredEntity
  (st/merge ACLEntity
            {(sc/optional-key :groups) [Str]}))

(defmacro defschema [name-sym ddl spec-kw-ns]
  `(do
     (sc/defschema ~name-sym
       (f-schema/->schema ~ddl))
     (f-spec/->spec ~ddl ~spec-kw-ns)))

(defmacro def-stored-schema [name-sym ddl spec-kw-ns]
  `(do
     (sc/defschema ~name-sym
       (st/merge
        (f-schema/->schema ~ddl)
        ACLStoredEntity))
     (f-spec/->spec ~ddl ~spec-kw-ns)))

(defmacro def-acl-schema [name-sym ddl spec-kw-ns]
  `(do
     (sc/defschema ~name-sym
       (st/merge
        (f-schema/->schema ~ddl)
        ACLEntity))
     (f-spec/->spec ~ddl ~spec-kw-ns)))
;; actor

(def-acl-schema Actor
  as/Actor
  "actor")

(def-acl-schema NewActor
  as/NewActor
  "new-actor")

(def-stored-schema StoredActor
  as/StoredActor
  "stored-actor")

;; campaign

(def-acl-schema Campaign
  cs/Campaign
  "campaign")

(def-acl-schema NewCampaign
  cs/NewCampaign
  "new-campaign")

(def-stored-schema StoredCampaign
  cs/StoredCampaign
  "stored-campaign")

;; coa

(def-acl-schema COA
  coas/COA
  "coa")

(def-acl-schema NewCOA
  coas/NewCOA
  "new-coa")

(def-stored-schema StoredCOA
  coas/StoredCOA
  "stored-coa")

;; data-table

(def-acl-schema DataTable
  (fu/replace-either-with-any
   ds/DataTable)
  "data-table")

(def-acl-schema NewDataTable
  (fu/replace-either-with-any
   ds/NewDataTable)
  "new-data-table")

(def-stored-schema StoredDataTable
  (fu/replace-either-with-any
   ds/StoredDataTable)
  "stored-data-table")

;; exploit-target

(def-acl-schema ExploitTarget
  es/ExploitTarget
  "exploit-target")

(def-acl-schema NewExploitTarget
  es/NewExploitTarget
  "new-exploit-target")

(def-stored-schema StoredExploitTarget
  es/StoredExploitTarget
  "stored-exploit-target")

;; sighting

(def-acl-schema NewSighting
  ss/NewSighting
  "new-sighting")

(def-acl-schema Sighting
  ss/Sighting
  "sighting")

(def-stored-schema StoredSighting
  ss/StoredSighting
  "stored-sighting")

;; judgement

(def-acl-schema Judgement
  js/Judgement
  "judgement")

(def-acl-schema NewJudgement
  js/NewJudgement
  "new-judgement")

(def-stored-schema StoredJudgement
  js/StoredJudgement
  "stored-judgement")

;; verdict

(def-acl-schema Verdict
  vs/Verdict
  "verdict")

;; feedback

(def-acl-schema Feedback
  feedbacks/Feedback
  "feedback")

(def-acl-schema NewFeedback
  feedbacks/NewFeedback
  "new-feedback")

(def-stored-schema StoredFeedback
  feedbacks/StoredFeedback
  "stored-feedback")

;; incident

(def-acl-schema Incident
  is/Incident
  "incident")

(def-acl-schema NewIncident
  is/NewIncident
  "new-incident")

(def-stored-schema StoredIncident
  is/StoredIncident
  "stored-incident")

;; indicator

(sc/defschema Indicator
  (st/merge ACLEntity
            (f-schema/->schema
             (fu/replace-either-with-any
              ins/Indicator))))

(f-spec/->spec ins/Indicator "indicator")

(sc/defschema NewIndicator
  (st/merge
   (f-schema/->schema
    (fu/replace-either-with-any
     ins/NewIndicator))
   ACLEntity))

(f-spec/->spec ins/NewIndicator "new-indicator")

(sc/defschema StoredIndicator
  (st/merge (f-schema/->schema
             (fu/replace-either-with-any
              ins/StoredIndicator))
            ACLStoredEntity))

(f-spec/->spec ins/StoredIndicator "stored-indicator")




;; relationship

(def-acl-schema Relationship
  rels/Relationship
  "relationship")

(def-acl-schema NewRelationship
  rels/NewRelationship
  "new-relationship")

(def-stored-schema StoredRelationship
  rels/StoredRelationship
  "stored-relationship")

;; Attack Pattern

(def-acl-schema AttackPattern
  attack/AttackPattern
  "attack-pattern")

(def-acl-schema NewAttackPattern
  attack/NewAttackPattern
  "new-attack-pattern")

(def-stored-schema StoredAttackPattern
  attack/StoredAttackPattern
  "stored-attack-pattern")

;; Malware

(def-acl-schema Malware
  malware/Malware
  "malware")

(def-acl-schema NewMalware
  malware/NewMalware
  "new-malware")

(def-stored-schema StoredMalware
  malware/StoredMalware
  "stored-malware")

;; Tool

(def-acl-schema Tool
  tool/Tool
  "tool")

(def-acl-schema NewTool
  tool/NewTool
  "new-tool")

(def-stored-schema StoredTool
  tool/StoredTool
  "stored-tool")

;; common

(sc/defschema TLP
  (f-schema/->schema
   cos/TLP))

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
   :attack-pattern StoredAttackPattern
   :campaign StoredCampaign
   :coa StoredCOA
   :exploit-target StoredExploitTarget
   :feedback StoredFeedback
   :incident StoredIncident
   :indicator StoredIndicator
   :judgement StoredJudgement
   :malware StoredMalware
   :relationship StoredRelationship
   :sighting StoredSighting
   :tool StoredTool})
