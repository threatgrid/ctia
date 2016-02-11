(ns cia.schemas.verdict
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema Verdict
  "A Verdict is chosen from all of the Judgements on that Observable
which have not yet expired.  The highest priority Judgement becomes
the active verdict.  If there is more than one Judgement with that
priority, than Clean disposition has priority over all others, then
Malicious disposition, and so on down to Unknown.
"
  {:id c/ID
   :disposition c/DispositionNumber
   (s/optional-key :judgement) rel/JudgementReference
   (s/optional-key :disposition_name) c/DispositionName
   })

(s/defschema NewVerdict
  (st/dissoc Verdict
             :id))

(s/defn realize-verdict :- Verdict
  [new-verdict :- NewVerdict
   id :- s/Str]
  (assoc new-verdict
         :id id))
