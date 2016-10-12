(ns ctia.schemas.bulk
  (:require
   [schema.core :as s]
   [schema-tools.core :as st]
   [ctia.schemas.core
    :refer [NewActor
            StoredActor
            NewCampaign
            StoredCampaign
            NewCOA
            StoredCOA
            NewExploitTarget
            StoredExploitTarget
            NewFeedback
            StoredFeedback
            NewIncident
            StoredIncident
            NewIndicator
            StoredIndicator
            NewJudgement
            StoredJudgement
            NewSighting
            StoredSighting
            NewTTP
            StoredTTP
            Reference]]))

(s/defschema StoredBulk
  (st/optional-keys
   {:actors          [(s/maybe StoredActor)]
    :campaigns       [(s/maybe StoredCampaign)]
    :coas            [(s/maybe StoredCOA)]
    :exploit-targets [(s/maybe StoredExploitTarget)]
    :feedbacks       [(s/maybe StoredFeedback)]
    :incidents       [(s/maybe StoredIncident)]
    :indicators      [(s/maybe StoredIndicator)]
    :judgements      [(s/maybe StoredJudgement)]
    :sightings       [(s/maybe StoredSighting)]
    :ttps            [(s/maybe StoredTTP)]}))

(s/defschema BulkRefs
  (st/optional-keys
   {:actors          [(s/maybe Reference)]
    :campaigns       [(s/maybe Reference)]
    :coas            [(s/maybe Reference)]
    :exploit-targets [(s/maybe Reference)]
    :feedbacks       [(s/maybe Reference)]
    :incidents       [(s/maybe Reference)]
    :indicators      [(s/maybe Reference)]
    :judgements      [(s/maybe Reference)]
    :sightings       [(s/maybe Reference)]
    :ttps            [(s/maybe Reference)]}))

(s/defschema NewBulk
  (st/optional-keys
   {:actors          [NewActor]
    :campaigns       [NewCampaign]
    :coas            [NewCOA]
    :exploit-targets [NewExploitTarget]
    :feedbacks       [NewFeedback]
    :incidents       [NewIncident]
    :indicators      [NewIndicator]
    :judgements      [NewJudgement]
    :sightings       [NewSighting]
    :ttps            [NewTTP]}))
