(ns cia.schemas.coa
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st]))

(s/defschema COA
  (merge
   c/GenericStixIdentifiers
   {:timestamp c/Time
    (s/optional-key :stage)
    (describe v/COAStage "stage in the cyber threat management lifecycle this CourseOfAction is relevant to")
    (s/optional-key :type)
    (describe v/COAType "type of this Course Of Action")
    (s/optional-key :objective)
    (describe [s/Str] "characterizes the objective of this Course Of Action") ;; Squashed / simplified
    (s/optional-key :impact)
    (describe s/Str "estimated impact of applying this Course Of Action")
    (s/optional-key :cost)
    (describe v/HighMedLow "estimated cost for applying this Course Of Action")
    (s/optional-key :efficacy)
    (describe v/HighMedLow "effectiveness of this Course Of Action in achieving its targeted Objective")
    (s/optional-key :source)
    (describe c/Source "source of this course of action")
    (s/optional-key :related_COAs)
    (describe rel/RelatedCOAs "relationships to one or more related courses of action")

    ;; Not provided: handling
    ;; Not provided: parameter_observables ;; Technical params using the CybOX language
    ;; Not provided: structured_COA ;; actionable structured representation for automation
    }))

(s/defschema NewCOA
  "Schema for submitting new COAs"
  (st/dissoc COA
             :id))

(s/defn realize-coa :- COA
  [new-coa :- NewCOA
   id :- s/Str]
  (st/assoc new-coa
            :id id))
