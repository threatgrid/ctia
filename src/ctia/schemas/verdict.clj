(ns ctia.schemas.verdict
  (:require [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema Verdict
  "A Verdict is chosen from all of the Judgements on that Observable
which have not yet expired.  The highest priority Judgement becomes
the active verdict.  If there is more than one Judgement with that
priority, than Clean disposition has priority over all others, then
Malicious disposition, and so on down to Unknown.
"
  {:disposition c/DispositionNumber
   (s/optional-key :judgement_id) rel/JudgementReference
   (s/optional-key :disposition_name) c/DispositionName})
