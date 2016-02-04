(ns cia.schemas.verdict
  (:require [cia.schemas.common :as c]
            [cia.schemas.relationships :as rel]
            [schema.core :as s]))

(s/defschema Verdict
  "A Verdict is chosen from all of the Judgements on that Observable
which have not yet expired.  The highest priority Judgement becomes
the active verdict.  If there is more than one Judgement with that
priority, than Clean disposition has priority over all others, then
Malicious disposition, and so on down to Unknown.
"
  {:disposition c/DispositionNumber
   (s/optional-key :judgement) rel/JudgementReference
   (s/optional-key :disposition_name) c/DispositionName
   })
