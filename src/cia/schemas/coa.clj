(ns cia.schemas.coa
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema COA
  (merge
   c/GenericStixIdentifiers
   {:timestamp c/Time
    (s/optional-key :stage) v/COAStage
    (s/optional-key :type) v/COAType
    (s/optional-key :objective) [s/Str] ;; Squashed / simplified
    (s/optional-key :impact) s/Str
    (s/optional-key :cost) v/HighMedLow
    (s/optional-key :efficacy) v/HighMedLow
    (s/optional-key :source) s/Str
    (s/optional-key :related_COAs) rel/RelatedCOAs

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
