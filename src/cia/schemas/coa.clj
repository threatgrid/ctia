(ns cia.schemas.coa
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [cia.schemas.vocabularies :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema COA
  (merge
   c/GenericStixIdentifiers
   {:valid_time c/ValidTime
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
    id :- s/Str]
   (realize-coa new-coa id nil))
  ([new-coa :- NewCOA
    id :- s/Str
    prev-coa :- (s/maybe StoredCOA)]
   (let [now (c/timestamp)]
     (st/assoc new-coa
               :id id
               :owner "not implemented"
               :created (or (:created prev-coa) now)
               :modified now
               :valid_time (or (:valid_time prev-coa)
                               {:end_time (or (get-in new-coa [:valid_time :end_time])
                                              c/default-expire-date)
                                :start_time (or (get-in new-coa [:valid_time :start_time])
                                                now)})))))
