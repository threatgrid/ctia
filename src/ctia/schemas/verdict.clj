(ns ctia.schemas.verdict
  (:require [ctia.schemas.common :as c]
            [ctia.schemas.relationships :as rel]
            [ctia.lib.time :as time]
            [schema.core :as s]
            [schema-tools.core :as st])
  (:import [java.util UUID]))

(s/defschema Type
  (s/enum "verdict"))

(s/defschema Verdict
  "A Verdict is chosen from all of the Judgements on that Observable
which have not yet expired.  The highest priority Judgement becomes
the active verdict.  If there is more than one Judgement with that
priority, then Clean disposition has priority over all others, then
Malicious disposition, and so on down to Unknown.
"
  {:type Type
   :disposition c/DispositionNumber
   (s/optional-key :judgement_id) rel/JudgementReference
   (s/optional-key :disposition_name) c/DispositionName})

(s/defschema StoredVerdict
  "A Verdict as stored in the data store"
  (st/merge Verdict
            {:id s/Str
             :type Type
             :owner s/Str
             :created c/Time}))

(s/defn make-id :- s/Str
  []
  (str "verdict-" (UUID/randomUUID)))

(s/defn realize-verdict :- StoredVerdict
  ([new-verdict :- Verdict
    login :- s/Str]
   (realize-verdict new-verdict (make-id) login))
  ([new-verdict :- Verdict
    id :- s/Str
    login :- s/Str]
   (let [now (time/now)]
     (assoc new-verdict
            :id id
            :owner login
            :created now))))
