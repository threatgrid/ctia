(ns cia.models
  (:require [cia.schemas.vocabularies :refer :all]
            [ring.swagger.schema :refer [coerce!]]
            [schema.core :as s]))


;;Allowed disposition values are:
(def disposition-map
  "Map of disposition numeric values to disposition names, as humans might use them."
  {1 "Clean"
   2 "Malicious"
   3 "Suspicious"
   4 "Common"
   5 "Unknown"})

(def DispositionNumber
  "Numeric verdict identifiers"
  (apply s/enum (keys disposition-map)))

(def DispositionName
  "String verdict identifiers"
  (apply s/enum (vals disposition-map)))

(def Severity s/Int)

(def Priority
  "A value 0-100 that determiend the priority of a judgement.  Curated
  feeds of black/whitelists, for example known good products within
  your organizations, should use a 95. All automated systems should
  use a priority of 90, or less.  Human judgements should have a
  priority of 100, so that humans can always override machines."
  s/Int)


(def CIAFeature
  (s/enum "Judgements" "Verdicts"
          "Threats" "Relations" "Feeds"
          "Feedback" "COAs" "ExploitTargets"))

(s/defschema VersionInfo
  {:id Long
   :base URI
   :version String
   :beta Boolean
   :supported_features [s/Str]})

(def default-version-info
  {:id "local-cia"
   :base "http://localhost:3000"
   :version "0.1"
   :supported_features ["Judgements" "Verdicts" "JudgementIndicators"]})

(s/defschema Verdict
  "A Verdict is chosen from all of the Judgements on that Observable
which have not yet expired.  The highest priority Judgement becomes
the active verdict.  If there is more than one Judgement with that
priority, than Clean disposition has priority over all others, then
Malicious disposition, and so on down to Unknown.
"
  {:disposition DispositionNumber
   (s/optional-key :judgement) ID
   (s/optional-key :disposition_name) DispositionName
   })

(s/defschema Judgement
  "A judgement about the intent or nature of an Observable.  For
  example, is it malicious, meaning is is malware and subverts system
  operations.  It could also be clean and be from a known benign, or
  trusted source.  It could also be common, something so widespread
  that it's not likely to be malicious."
  {:id ID
   :observable Observable
   :disposition DispositionNumber
   :source s/Str
   :priority Priority
   :confidence Confidence
   :severity Severity
   :timestamp Time
   (s/optional-key :reason) s/Str
   (s/optional-key :disposition_name) DispositionName
   (s/optional-key :expires) Time

   (s/optional-key :source_uri) URI

   (s/optional-key :reason_uri) URI

   (s/optional-key :indicators) [Reference]
   }
  )

(def NewJudgement
  "Schema for submitting new Judgements."
  (merge (dissoc Judgement :id
                 :priority
                 :timestamp
                 :severity
                 :confidence)
         {(s/optional-key :severity) Severity
          (s/optional-key :confidence) Confidence
          (s/optional-key :timestamp) Time
          (s/optional-key :priority) Priority}))

(def StoredJudgement
  "A judgement at rest in the storage service"
  (merge Judgement
         {:owner s/Str
          :created Time}))

(s/defschema Feedback
  "Feedback on a Judgement or Verdict.  Is it wrong?  If so why?  Was
  it right-on, and worthy of confirmation?"
  {:id s/Num
   :judgement_id s/Num
   (s/optional-key :source) s/Str
   :feedback (s/enum -1 0 1)
   :reason s/Str})

(s/defschema NewFeedback
  "Schema for submitting new Feedback"
  (dissoc Feedback :id))

(def StoredFeedback
  "A feedback record at rest in the storage service"
  (merge Feedback
         {:owner s/Str
          :timestamp Time}))


(def SpecificationType
  "Types of Indicator we support Currently only Judgement indicators,
  which contain a list of Judgements associated with this indicator."
  (s/enum "Judgement" "ThreatBrain" "SIOC" "Snort" "OpenIOC"))


(defonce id-seq (atom 0))
(defonce judgements (atom (array-map)))

(defn get-judgement [id] (@judgements id))
(defn get-judgements [] (-> judgements deref vals reverse))
(defn find-judgements
  ([kind]
   (filter #(= kind (:observable_type %))
           (get-judgements)))
  ([kind val]
   (filter #(and (= kind (:observable_type %))
                 (= val (:observable %)))
           (get-judgements))))


(defn current-verdict [kind val]
  (first (find-judgements kind val)))

(defn delete! [id] (swap! judgements dissoc id) nil)

(defn add! [new-judgement]
  (let [id (swap! id-seq inc)
        disp (coerce! Judgement (assoc new-judgement :id id))]
    (swap! judgements assoc id disp)
    disp))
