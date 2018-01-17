(ns ctia.schemas.core
  (:require
   [flanders.utils :as fu]
   [ctim.schemas
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
    [investigation :as inv]
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

(def-acl-schema PartialActor
  (fu/optionalize-all as/Actor)
  "partial-actor")

(sc/defschema PartialActorList
  [PartialActor])

(def-acl-schema NewActor
  as/NewActor
  "new-actor")

(def-stored-schema StoredActor
  as/StoredActor
  "stored-actor")

(def-stored-schema PartialStoredActor
  (fu/optionalize-all as/StoredActor)
  "partial-stored-actor")

;; campaign

(def-acl-schema Campaign
  cs/Campaign
  "campaign")

(def-acl-schema PartialCampaign
  (fu/optionalize-all cs/Campaign)
  "partial-campaign")

(sc/defschema PartialCampaignList
  [PartialCampaign])

(def-acl-schema NewCampaign
  cs/NewCampaign
  "new-campaign")

(def-stored-schema StoredCampaign
  cs/StoredCampaign
  "stored-campaign")

(def-stored-schema PartialStoredCampaign
  (fu/optionalize-all cs/StoredCampaign)
  "partial-stored-campaign")

;; coa

(def-acl-schema COA
  coas/COA
  "coa")

(def-acl-schema PartialCOA
  (fu/optionalize-all coas/COA)
  "partial-coa")

(sc/defschema PartialCOAList
  [PartialCOA])

(def-acl-schema NewCOA
  coas/NewCOA
  "new-coa")

(def-stored-schema StoredCOA
  coas/StoredCOA
  "stored-coa")

(def-stored-schema PartialStoredCOA
  (fu/optionalize-all coas/StoredCOA)
  "partial-stored-coa")

;; data-table

(def-acl-schema DataTable
  (fu/replace-either-with-any
   ds/DataTable)
  "data-table")

(def-acl-schema PartialDataTable
  (fu/optionalize-all
   (fu/replace-either-with-any
    ds/DataTable))
  "partial-data-table")

(sc/defschema PartialDataTableList
  [PartialDataTable])

(def-acl-schema NewDataTable
  (fu/replace-either-with-any
   ds/NewDataTable)
  "new-data-table")

(def-stored-schema StoredDataTable
  (fu/replace-either-with-any
   ds/StoredDataTable)
  "stored-data-table")

(def-stored-schema PartialStoredDataTable
  (fu/optionalize-all
   (fu/replace-either-with-any
    ds/StoredDataTable))
  "partial-stored-data-table")


;; exploit-target

(def-acl-schema ExploitTarget
  es/ExploitTarget
  "exploit-target")

(def-acl-schema PartialExploitTarget
  (fu/optionalize-all es/ExploitTarget)
  "partial-exploit-target")

(sc/defschema PartialExploitTargetList
  [PartialExploitTarget])

(def-acl-schema NewExploitTarget
  es/NewExploitTarget
  "new-exploit-target")

(def-stored-schema StoredExploitTarget
  es/StoredExploitTarget
  "stored-exploit-target")

(def-stored-schema PartialStoredExploitTarget
  (fu/optionalize-all es/StoredExploitTarget)
  "partial-stored-exploit-target")


;; sighting

(def-acl-schema NewSighting
  ss/NewSighting
  "new-sighting")

(def-acl-schema Sighting
  ss/Sighting
  "sighting")

(def-acl-schema PartialSighting
  (fu/optionalize-all ss/Sighting)
  "partial-sighting")

(sc/defschema PartialSightingList
  [PartialSighting])

(def-stored-schema StoredSighting
  ss/StoredSighting
  "stored-sighting")

(def-stored-schema PartialStoredSighting
  (fu/optionalize-all ss/StoredSighting)
  "partial-stored-sighting")

;; judgement

(def-acl-schema Judgement
  js/Judgement
  "judgement")

(def-acl-schema PartialJudgement
  (fu/optionalize-all js/Judgement)
  "partial-judgement")

(sc/defschema PartialJudgementList
  [PartialJudgement])

(def-acl-schema NewJudgement
  js/NewJudgement
  "new-judgement")

(def-stored-schema StoredJudgement
  js/StoredJudgement
  "stored-judgement")

(def-stored-schema PartialStoredJudgement
  (fu/optionalize-all js/StoredJudgement)
  "partial-stored-judgement")

;; verdict

(def-acl-schema Verdict
  vs/Verdict
  "verdict")

;; feedback

(def-acl-schema Feedback
  feedbacks/Feedback
  "feedback")

(def-acl-schema PartialFeedback
  (fu/optionalize-all feedbacks/Feedback)
  "partial-feedback")

(sc/defschema PartialFeedbackList
  [PartialFeedback])

(def-acl-schema NewFeedback
  feedbacks/NewFeedback
  "new-feedback")

(def-stored-schema StoredFeedback
  feedbacks/StoredFeedback
  "stored-feedback")

(def-stored-schema PartialStoredFeedback
  (fu/optionalize-all feedbacks/StoredFeedback)
  "partial-stored-feedback")

;; incident


(def-acl-schema Incident
  is/Incident
  "incident")

(def-acl-schema PartialIncident
  (fu/optionalize-all is/Incident)
  "partial-incident")

(sc/defschema PartialIncidentList
  [PartialIncident])

(def-acl-schema NewIncident
  is/NewIncident
  "new-incident")

(def-stored-schema StoredIncident
  is/StoredIncident
  "stored-incident")

(def-stored-schema PartialStoredIncident
  (fu/optionalize-all is/StoredIncident)
  "partial-stored-incident")


;; indicator

(sc/defschema Indicator
  (st/merge ACLEntity
            (f-schema/->schema
             (fu/replace-either-with-any
              ins/Indicator))))

(f-spec/->spec ins/Indicator "indicator")

(sc/defschema PartialIndicator
  (st/merge ACLEntity
            (f-schema/->schema
             (fu/optionalize-all
              (fu/replace-either-with-any
               ins/Indicator)))))

(sc/defschema PartialIndicatorList
  [PartialIndicator])

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

(sc/defschema PartialStoredIndicator
  (st/merge (f-schema/->schema
             (fu/optionalize-all
              (fu/replace-either-with-any
               ins/StoredIndicator)))
            ACLStoredEntity))

;; investigation

(sc/defschema Investigation
  (st/merge (f-schema/->schema inv/Investigation)
            ACLEntity
            {sc/Keyword sc/Any}))

(f-spec/->spec inv/Investigation "investigation")

(sc/defschema PartialInvestigation
  (st/merge (f-schema/->schema (fu/optionalize-all inv/Investigation))
            ACLEntity
            {sc/Keyword sc/Any}))

(sc/defschema PartialInvestigationList
  [PartialInvestigation])

(sc/defschema NewInvestigation
  (st/merge (f-schema/->schema inv/NewInvestigation)
            ACLEntity
            {sc/Keyword sc/Any}))

(f-spec/->spec inv/NewInvestigation "new-investigation")

(sc/defschema StoredInvestigation
  (st/merge (f-schema/->schema inv/StoredInvestigation)
            ACLStoredEntity
            {sc/Keyword sc/Any}))

(f-spec/->spec inv/StoredInvestigation "stored-investigation")

(sc/defschema PartialStoredInvestigation
  (st/merge (f-schema/->schema (fu/optionalize-all inv/StoredInvestigation))
            ACLStoredEntity
            {sc/Keyword sc/Any}))

;; relationship


(def-acl-schema Relationship
  rels/Relationship
  "relationship")

(def-acl-schema PartialRelationship
  (fu/optionalize-all rels/Relationship)
  "partial-relationship")

(sc/defschema PartialRelationshipList
  [PartialRelationship])

(def-acl-schema NewRelationship
  rels/NewRelationship
  "new-relationship")

(def-stored-schema StoredRelationship
  rels/StoredRelationship
  "stored-relationship")

(def-stored-schema PartialStoredRelationship
  (fu/optionalize-all rels/StoredRelationship)
  "partial-stored-relationship")

;; Attack Pattern


(def-acl-schema AttackPattern
  attack/AttackPattern
  "attack-pattern")

(def-acl-schema PartialAttackPattern
  (fu/optionalize-all attack/AttackPattern)
  "partial-attack-pattern")

(sc/defschema PartialAttackPatternList
  [PartialAttackPattern])

(def-acl-schema NewAttackPattern
  attack/NewAttackPattern
  "new-attack-pattern")

(def-stored-schema StoredAttackPattern
  attack/StoredAttackPattern
  "stored-attack-pattern")

(def-stored-schema PartialStoredAttackPattern
  (fu/optionalize-all attack/StoredAttackPattern)
  "partial-stored-attack-pattern")

;; Malware

(def-acl-schema Malware
  malware/Malware
  "malware")

(def-acl-schema PartialMalware
  (fu/optionalize-all malware/Malware)
  "partial-malware")

(sc/defschema PartialMalwareList
  [PartialMalware])

(def-acl-schema NewMalware
  malware/NewMalware
  "new-malware")

(def-stored-schema StoredMalware
  malware/StoredMalware
  "stored-malware")

(def-stored-schema PartialStoredMalware
  (fu/optionalize-all malware/StoredMalware)
  "partial-stored-malware")


;; Tool

(def-acl-schema Tool
  tool/Tool
  "tool")

(def-acl-schema PartialTool
  (fu/optionalize-all tool/Tool)
  "partial-tool")

(sc/defschema PartialToolList [PartialTool])

(def-acl-schema NewTool
  tool/NewTool
  "new-tool")

(def-stored-schema StoredTool
  tool/StoredTool
  "stored-tool")

(def-stored-schema PartialStoredTool
  (fu/optionalize-all tool/StoredTool)
  "partial-stored-tool")

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

(def TransientID sc/Str)

(sc/defschema TempIDs
  "Mapping table between transient and permanent IDs"
  {TransientID ID})

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
