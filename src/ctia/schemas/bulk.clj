(ns ctia.schemas.bulk
  (:require [ctim.schemas
             [actor :as actor]
             [campaign :as campaign]
             [coa :as coa]
             [common :as c]
             [exploit-target :as et]
             [feedback :as feedback]
             [incident :as incident]
             [indicator :as indicator]
             [judgement :as judgement]
             [sighting :as sighting]
             [ttp :as ttp]]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema StoredBulk
  (st/optional-keys
   {:actors          [(s/maybe actor/StoredActor)]
    :campaigns       [(s/maybe campaign/StoredCampaign)]
    :coas            [(s/maybe coa/StoredCOA)]
    :exploit-targets [(s/maybe et/StoredExploitTarget)]
    :feedbacks       [(s/maybe feedback/StoredFeedback)]
    :incidents       [(s/maybe incident/StoredIncident)]
    :indicators      [(s/maybe indicator/StoredIndicator)]
    :judgements      [(s/maybe judgement/StoredJudgement)]
    :sightings       [(s/maybe sighting/StoredSighting)]
    :ttps            [(s/maybe ttp/StoredTTP)]}))

(s/defschema BulkRefs
  (st/optional-keys
   {:actors          [(s/maybe c/Reference)]
    :campaigns       [(s/maybe c/Reference)]
    :coas            [(s/maybe c/Reference)]
    :exploit-targets [(s/maybe c/Reference)]
    :feedbacks       [(s/maybe c/Reference)]
    :incidents       [(s/maybe c/Reference)]
    :indicators      [(s/maybe c/Reference)]
    :judgements      [(s/maybe c/Reference)]
    :sightings       [(s/maybe c/Reference)]
    :ttps            [(s/maybe c/Reference)]}))

(s/defschema NewBulk
  (st/optional-keys
   {:actors          [actor/NewActor]
    :campaigns       [campaign/NewCampaign]
    :coas            [coa/NewCOA]
    :exploit-targets [et/NewExploitTarget]
    :feedbacks       [feedback/NewFeedback]
    :incidents       [incident/NewIncident]
    :indicators      [indicator/NewIndicator]
    :judgements      [judgement/NewJudgement]
    :sightings       [sighting/NewSighting]
    :ttps            [ttp/NewTTP]}))
