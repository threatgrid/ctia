(ns ctia.schemas.coa
  (:require [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.vocabularies :as v]
            [schema.core :as s]
            [ring.swagger.schema :refer [describe]]
            [schema-tools.core :as st]))

(s/defschema COA
  (merge
   c/GenericStixIdentifiers
   {:valid_time c/ValidTime
    (s/optional-key :stage)

    (describe v/COAStage
              "specifies what stage in the cyber threat management lifecycle this Course Of Action is relevant to ")

    (s/optional-key :type)
    (describe v/COAType
              "type of this CourseOfAction")

    (s/optional-key :objective)
    (describe [s/Str]
              "characterizes the objective of this Course Of Action") ;; Squashed / simplified

    (s/optional-key :impact)
    (describe s/Str
              "characterizes the estimated impact of applying this Course Of Action")

    (s/optional-key :cost)
    (describe v/HighMedLow
              "characterizes the estimated cost for applying this Course Of Action")

    (s/optional-key :efficacy)
    (describe v/HighMedLow
              "effectiveness of this Course Of Action in achieving its targeted Objective")

    (s/optional-key :source)
    (describe s/Str
              "Source of this Course Of Action")

    (s/optional-key :related_COAs)
    (describe rel/RelatedCOAs
              "identifies or characterizes relationships to one or more related courses of action")

    ;; Not provided: handling
    ;; Not provided: parameter_observables ;; Technical params using the CybOX language
    ;; Not provided: structured_COA ;; actionable structured representation for automation
    }))

(s/defschema NewCOA
  "Schema for submitting new COAs"
  (st/merge
   (st/dissoc COA
              :id
              :valid_time)
   {(s/optional-key :valid_time) c/ValidTime}))

(s/defschema StoredCOA
  "A COA as stored in the data store"
  (st/merge COA
            {:owner s/Str
             :created c/Time
             :modified c/Time}))

(s/defn realize-coa :- StoredCOA
  ([new-coa :- NewCOA
    id :- s/Str
    login :- s/Str]
   (realize-coa new-coa id login nil))
  ([new-coa :- NewCOA
    id :- s/Str
    login :- s/Str
    prev-coa :- (s/maybe StoredCOA)]
   (let [now (c/timestamp)]
     (st/assoc new-coa
               :id id
               :owner login
               :created (or (:created prev-coa) now)
               :modified now
               :valid_time (or (:valid_time prev-coa)
                               {:end_time (or (get-in new-coa [:valid_time :end_time])
                                              c/default-expire-date)
                                :start_time (or (get-in new-coa [:valid_time :start_time])
                                                now)})))))
