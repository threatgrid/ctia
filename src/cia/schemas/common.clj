(ns cia.schemas.common
  (:require [cia.schemas.vocabularies :as v]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [ring.util.http-response :as http-response]
            [ring.swagger.schema :refer [describe]]
            [schema.core :as s]))

(def Reference
  "An entity ID, or a URI referring to a remote one."
  s/Str)

(def ID
  "A string uniquely identifying an entity."
  s/Str)

(def URI
  "A URI."
  s/Str)

(def Time
  "Schema definition for all date or timestamp values in GUNDAM."
  org.joda.time.DateTime)

(s/defschema VersionInfo
  {:base URI
   :version s/Str
   :beta s/Bool
   :supported_features [s/Str]})

(def CIAFeature
  (s/enum "Judgements"
          "Verdicts"
          "Threats"
          "Relations"
          "Feeds"
          "Feedback"
          "COAs"
          "ExploitTargets"))

(def SpecificationType
  "Types of Indicator we support Currently only Judgement indicators,
  which contain a list of Judgements associated with this indicator."
  (s/enum "Judgement"
          "ThreatBrain"
          "SIOC"
          "Snort"
          "OpenIOC"))

(s/defschema MinimalStixIdentifiers
  {;; :id and :idref must be implemented exclusively
   :id ID})

(s/defschema GenericStixIdentifiers
  "These fields are common in STIX data models"
  (merge
   MinimalStixIdentifiers
   {:title s/Str
    :description s/Str
    (s/optional-key :short_description) s/Str}))

(s/defschema Tool
  "See http://stixproject.github.io/data-model/1.2/cyboxCommon/ToolInformationType/"
  {:description s/Str
   (s/optional-key :type)
   (describe [v/AttackToolType] "type of the tool leveraged")
   (s/optional-key :references)
   (describe [s/Str] "references to instances or additional information for this tool")
   (s/optional-key :vendor)
   (describe s/Str "information identifying the vendor organization for this tool")
   (s/optional-key :version)
   (describe s/Str "version descriptor of this tool")
   (s/optional-key :service_pack)
   (describe s/Str "service pack descriptor for this tool")
   ;; Not provided: tool_specific_data
   ;; Not provided: tool_hashes
   ;; Not provided: tool_configuration
   ;; Not provided: execution_environment
   ;; Not provided: errors
   ;; Not provided: metadata
   ;; Not provided: compensation_model
   })

(s/defschema ScopeWrapper
  "For merging into other structures; Commonly repeated structure"
  {(s/optional-key :scope) v/Scope})

(s/defschema Contributor
  "See http://stixproject.github.io/data-model/1.2/cyboxCommon/ContributorType/"
  {(s/optional-key :role)
   (describe s/Str "role played by this contributor")
   (s/optional-key :name)
   (describe s/Str "name of this contributor")
   (s/optional-key :email)
   (describe s/Str "email of this contributor")
   (s/optional-key :phone)
   (describe s/Str "telephone number of this contributor")
   (s/optional-key :organization)
   (describe s/Str "organization name of this contributor")
   (s/optional-key :date)
   (describe Time "description (bounding) of the timing of this contributor's involvement")
   (s/optional-key :contribution_location)
   (describe s/Str "information describing the location at which the contributory activity occured")})

(s/defschema RelatedIdentity
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedIdentityType/"
  {(s/optional-key :confidence)
   (describe v/HighMedLow "specifies the level of confidence in the assertion of the relationship between the two components")
   (s/optional-key :information_source)
   (describe s/Str "specifies the source of the information about the relationship between the two components")
   (s/optional-key :relationship) s/Str ;; empty vocab
   :identity (describe Reference "A reference to or representation of the related Identity") ;; Points to Identity
   })

(s/defschema Identity
  "See http://stixproject.github.io/data-model/1.2/stixCommon/IdentityType/"
  {:description s/Str
   :related_identities
   (describe [RelatedIdentity] "identifies other entity Identities related to this entity Identity")})

(s/defschema Activity
  "See http://stixproject.github.io/data-model/1.2/stixCommon/ActivityType/"
  {:date_time (describe Time "specifies the date and time at which the activity occured")
   :description (describe s/Str "a description of the activity")})

(s/defschema Observable
  "A simple, atomic value which has a consistent identity, and is
  stable enough to be attributed an intent or nature.  This is the
  classic 'indicator' which might appear in a data feed of bad IPs, or
  bad Domains."
  {:value s/Str
   :type v/ObservableType})

(s/defschema ValidTime
  "See http://stixproject.github.io/data-model/1.2/indicator/ValidTimeType/"
  {(s/optional-key :start_time)
   (describe Time "If not present, the valid time position of the indicator does not have a lower bound")
   (s/optional-key :end_time)
   (describe Time "If not present, the valid time position of the indicator does not have an upper bound")})

;;Allowed disposition values are:
(def disposition-map
  "Map of disposition numeric values to disposition names, as humans might use them."
  {1 "Clean"
   2 "Malicious"
   3 "Suspicious"
   4 "Common"
   5 "Unknown"})

(def disposition-map-inverted
  (clojure.set/map-invert disposition-map))

(def DispositionNumber
  "Numeric verdict identifiers"
  (apply s/enum (keys disposition-map)))

(def DispositionName
  "String verdict identifiers"
  (apply s/enum (vals disposition-map)))

(s/defschema HttpParams
  "HTTP Parameters. TODO: Presuming either keyword or string keys for now."
  {s/Any s/Any})

;; helper fns used by schemas

(defn determine-disposition-id
  "Takes a judgement and determines the disposition.
   Defaults to 'Unknown' disposition (in case none is provided).
   Throws an bad-request! if the provided disposition and
   disposition_name do not match."
  [{:keys [disposition disposition_name] :as judgement}]
  (cond
    (every? nil? [disposition disposition_name]) (get disposition-map-inverted "Unknown")
    (nil? disposition) (get disposition-map-inverted disposition_name)
    (nil? disposition_name) disposition
    (= disposition (get disposition-map-inverted disposition_name)) disposition
    :else (http-response/bad-request!
           {:error "Mismatching :dispostion and dispositon_name for judgement"
            :judgement judgement})))

(defn timestamp
  ([]
   (time/now))
  ([time-str]
   (if (nil? time-str)
     (time/now)
     (time-format/parse (time-format/formatters :date-time)
                        time-str))))

(def default-expire-date (time/date-time 2525 1 1))
