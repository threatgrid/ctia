(ns ctia.schemas.bulk
  (:require
   [schema.core :as s]
   [schema-tools.core :as st]
   [ctia.schemas.core :refer :all]))

(s/defschema EntityError
  "Error related to one entity of the bulk"
  {:error s/Any})

(s/defschema Bulk
  (st/optional-keys
   {:actors          [(s/maybe Actor)]
    :attack_patterns [(s/maybe AttackPattern)]
    :campaigns       [(s/maybe Campaign)]
    :coas            [(s/maybe COA)]
    :data_tables     [(s/maybe DataTable)]
    :exploit_targets [(s/maybe ExploitTarget)]
    :feedbacks       [(s/maybe Feedback)]
    :incidents       [(s/maybe Incident)]
    :indicators      [(s/maybe Indicator)]
    :investigations  [(s/maybe Investigation)]
    :judgements      [(s/maybe Judgement)]
    :malwares        [(s/maybe Malware)]
    :relationships   [(s/maybe Relationship)]
    :casebooks       [(s/maybe Casebook)]
    :sightings       [(s/maybe Sighting)]
    :tools           [(s/maybe Tool)]}))

(s/defschema StoredBulk
  (st/optional-keys
   {:actors          [(s/maybe StoredActor)]
    :attack_patterns [(s/maybe StoredAttackPattern)]
    :campaigns       [(s/maybe StoredCampaign)]
    :coas            [(s/maybe StoredCOA)]
    :data_tables     [(s/maybe StoredDataTable)]
    :exploit_targets [(s/maybe StoredExploitTarget)]
    :feedbacks       [(s/maybe StoredFeedback)]
    :incidents       [(s/maybe StoredIncident)]
    :investigations  [(s/maybe StoredInvestigation)]
    :indicators      [(s/maybe StoredIndicator)]
    :judgements      [(s/maybe StoredJudgement)]
    :malwares        [(s/maybe StoredMalware)]
    :relationships   [(s/maybe StoredRelationship)]
    :casebooks       [(s/maybe StoredCasebook)]
    :sightings       [(s/maybe StoredSighting)]
    :tools           [(s/maybe StoredTool)]}))

(s/defschema BulkRefs
  (st/optional-keys
   {:actors          [(s/maybe Reference)]
    :attack_patterns [(s/maybe Reference)]
    :campaigns       [(s/maybe Reference)]
    :coas            [(s/maybe Reference)]
    :data_tables     [(s/maybe Reference)]
    :exploit_targets [(s/maybe Reference)]
    :feedbacks       [(s/maybe Reference)]
    :incidents       [(s/maybe Reference)]
    :indicators      [(s/maybe Reference)]
    :investigations  [(s/maybe Reference)]
    :judgements      [(s/maybe Reference)]
    :malwares        [(s/maybe Reference)]
    :relationships   [(s/maybe Reference)]
    :casebooks       [(s/maybe Reference)]
    :sightings       [(s/maybe Reference)]
    :tools           [(s/maybe Reference)]
    :tempids         TempIDs}))

(s/defschema NewBulk
  (st/optional-keys
   {:actors          [NewActor]
    :attack_patterns [NewAttackPattern]
    :campaigns       [NewCampaign]
    :coas            [NewCOA]
    :data_tables     [NewDataTable]
    :exploit_targets [NewExploitTarget]
    :feedbacks       [NewFeedback]
    :incidents       [NewIncident]
    :indicators      [NewIndicator]
    :investigations  [NewInvestigation]
    :judgements      [NewJudgement]
    :malwares        [NewMalware]
    :relationships   [NewRelationship]
    :casebooks       [NewCasebook]
    :sightings       [NewSighting]
    :tools           [NewTool]}))
