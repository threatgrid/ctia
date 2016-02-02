(ns cia.schemas.coa
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]))

(s/defschema COA
  (merge
   c/GenericStixIdentifiers
   {(s/optional-key :timestamp) c/Time
    (s/optional-key :stage) v/COAStage
    (s/optional-key :type) v/COAType
    (s/optional-key :objective) [s/Str] ;; Squashed / simplified
    (s/optional-key :impact) s/Str
    (s/optional-key :cost) v/HighMedLow
    (s/optional-key :efficacy) v/HighMedLow
    (s/optional-key :source) c/Source
    (s/optional-key :related_COAs) rel/RelatedCOAs

    ;; Not provided: handling
    ;; Not provided: parameter_observables ;; Technical params using the CybOX language
    ;; Not provided: structured_COA ;; actionable structured representation for automation
    }))
