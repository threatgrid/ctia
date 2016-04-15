(ns ctia.schemas.coa
  (:require [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st]))

(s/defschema COA
  (merge
   c/GenericStixIdentifiers
   {:valid_time c/ValidTime}
   (st/optional-keys
    {:stage (describe v/COAStage "specifies what stage in the cyber threat management lifecycle this Course Of Action is relevant to")
     :coa_type (describe v/COAType "type of this CourseOfAction")
     :objective (describe [s/Str] "characterizes the objective of this Course Of Action") ;; Squashed / simplified
     :impact (describe s/Str "characterizes the estimated impact of applying this Course Of Action")
     :cost (describe v/HighMedLow "characterizes the estimated cost for applying this Course Of Action")
     :efficacy (describe v/HighMedLow "effectiveness of this Course Of Action in achieving its targeted Objective")
     :source (describe s/Str "Source of this Course Of Action")
     :related_COAs (describe rel/RelatedCOAs "identifies or characterizes relationships to one or more related courses of action")
     ;; Not provided: handling
     ;; Not provided: parameter_observables ;; Technical params using the CybOX language
     ;; Not provided: structured_COA ;; actionable structured representation for automation
     })))

(s/defschema NewCOA
  "Schema for submitting new COAs"
  (st/merge
   (st/dissoc COA :id :valid_time)
   (st/optional-keys
    {:valid_time c/ValidTime
     :type (s/enum "COA")})))

(s/defschema StoredCOA
  "An coa as stored in the data store"
  (c/stored-schema "COA" COA))

(def realize-coa
  (c/default-realize-fn "COA" NewCOA StoredCOA))
