(ns cia.models
  (:require [schema.core :as s]
            [ring.swagger.schema :refer [coerce!]]))


(def ObservableType
  "Observable type names"
  (s/enum "IP"
          "IPv6"
          "MAC"
          "User"
          "Domain"
          "SHA256"
          "MD5"
          "SHA1"
          "URL"))

(s/defschema Observable
  "A simple, atomic value which has a consistent identity, and is
  stable enough to be attributed an intent or nature.  This is the
  classic 'indicator' which might appear in a data feed of bad IPs, or
  bad Domains."
  {:value s/Str
   :type ObservableType})

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

(def Confidence (s/enum "Low" "Medium" "High"))
(def Severity s/Int)
(def Priority
  "A value 0-100 that determiend the priority of a judgement.  Curated
  feeds of black/whitelists, for example known good products within
  your organizations, should use a 95. All automated systems should
  use a priority of 90, or less.  Human judgements should have a
  priority of 100, so that humans can always override machines."
  s/Int)

(def ID
  "A string uniquely identifying an entity."
  s/Str)

(def URI
  "A URI."
  s/Str)

(def IDRef
  "A URI that points to the JSON representation of the object."
  s/Str)

(def Reference
  "An entity ID, or a URI referring to a remote one."
  s/Str)

(def Time
  "Schema definition for all date or timestamp values in GUNDAM."
  org.joda.time.DateTime)

(def CIAFeature
  (s/enum "Judgements" "Verdicts"
          "Threats" "Relations" "Feeds"
          "Feedback"))

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
  {:judgement ID
   :disposition DispositionNumber
   (s/optional-key :disposition_name) DispositionName
   })

(def Judgement
  "A judgement about the intent or nature of an Observable.  For
  example, is it malicious, meaning is is malware and subverts system
  operations.  It could also be clean and be from a known benign, or
  trusted source.  It could also be common, something so widespread
  that it's not likely to be malicious."
  (merge Verdict
         {
          :id ID
          :observable Observable
          (s/optional-key :expires) Time
          :source s/Str
          (s/optional-key :source_uri) URI 

          :priority Priority
          (s/optional-key :reason) s/Str
          (s/optional-key :reason_uri) URI
          
          (s/optional-key :confidence) Confidence
          (s/optional-key :severity) Severity
          
          :indicators [Reference]
          }))

(def NewJudgement
  "Schema for submitting new Judgements."
  (dissoc Judgement :id))

(def StoredJudgement
  "A judgement at rest in the storage service"
  (merge Judgement
         {:owner s/Str
          :timestamp Time}))

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


(s/defschema JudgementSpecification
  "An indicator based on a list of judgements.  If any of the
  Observables in it's judgements are encountered, than it may be
  matches against.  If there are any required judgements, they all
  must be matched in order for the indicator to be considered a
  match."
  {:type (s/eq "Judgement")
   :judgements [Reference]
   :required_judgements [Reference]})

(s/defschema ThreatBrainSpecification
  "An indicator which runs in threatbrain..."
  {:type (s/eq "ThreatBrain")
   :query s/Str
   :variables [s/Str] })

(s/defschema SnortSpecification
  "An indicator which runs in snort..."
  {:type (s/eq "Snort")
   :snort_sig s/Str})

(s/defschema SIOCSpecification
  "An indicator which runs in snort..."
  {:type (s/eq "SIOC")
   :sioc s/Str})

(s/defschema OpenIOCSpecification
  "An indicator which contains an XML blob of an openIOC indicator.."
  {:type (s/eq "OpenIOC")
   :openIOC s/Str})

(def SpecificationType
  "Types of Indicator we support Currently only Judgement indicators,
  which contain a list of Judgements associated with this indicator."
  (s/enum "Judgement" "ThreatBrain" "SIOC" "Snort" "OpenIOC"))


(s/defschema Indicator
  "See http://stixproject.github.io/data-model/1.2/indicator/IndicatorType/"
  {:id s/Str
   (s/optional-key :alternate_ids) [ID]
   
   (s/optional-key :version) s/Num

   :title s/Str
   
   (s/optional-key :short_description) s/Str ;; simple string only
   (s/optional-key :description) s/Str       ;; can be markdown

   (s/optional-key :expires) Time

   (s/optional-key :indicated_ttps) [Reference]
   (s/optional-key :kill_chain_phases) [s/Str] ;; fixed vocab

   (s/optional-key :test_mechanisms) [s/Str]
   (s/optional-key :likely_impact) s/Str  ;; fixed vocab

   (s/optional-key :handling) s/Str ;; fixed vocab
   (s/optional-key :confidence) Confidence

   (s/optional-key :related_indicators) [Reference]
   (s/optional-key :related_campaigns) [Reference]

   (s/optional-key :related_COAs) [Reference]

   :producer s/Str

   ;; we should use a conditional based on the :type field of the
   ;; specification, and not an either
   (s/optional-key :specifications) [(s/either
                                      JudgementSpecification
                                      ThreatBrainSpecification
                                      SnortSpecification
                                      SIOCSpecification
                                      OpenIOCSpecification
                                      )]})

(def NewIndicator
  (dissoc Indicator :id))

(def StoredIndicator
  "A feedback record at rest in the storage service"
  (merge Indicator
         {:owner s/Str
          :created Time
          :timestamp Time}
         ))


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
