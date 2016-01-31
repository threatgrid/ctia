(ns cia.models
  (:require [schema.core :as s]
            [ring.swagger.schema :refer [coerce!]]
            [clojure.string :as str]))

;; References
(def Reference
  "An entity ID, or a URI referring to a remote one."
  s/Str)
(def IndicatorReference Reference)

(def ID
  "A string uniquely identifying an entity."
  s/Str)

(def URI
  "A URI."
  s/Str)

(def IDRef
  "A URI that points to the JSON representation of the object."
  s/Str)

(def Time
  "Schema definition for all date or timestamp values in GUNDAM."
  org.joda.time.DateTime)

(s/defschema MinimalStixIdentifiers
  {;; :id and :idref must be implemented exclusively
   (s/required-key (s/enum :id :idref)) (s/either ID IDRef)})

(s/defschema GenericStixIdentifiers
  "These fields are common in STIX data models"
  (merge
   MinimalStixIdentifiers
   {:title s/Str
    :description [s/Str]
    (s/optional-key :sort_description) [s/Str]}))

(def ObservableType
  "Observable type names"
  (s/enum "ip"
          "ipv6"
          "mac"
          "user"
          "domain"
          "sha256"
          "md5"
          "sha1"
          "url"))

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

(def Confidence
  "See http://stixproject.github.io/data-model/1.2/stixVocabs/HighMediumLowVocab-1.0/"
  (s/enum "Low"
          "Medium"
          "High"
          "None"
          "Unknown"))

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

(def Scope
  (s/either "inclusive" "exclusive"))

(s/defschema ScopeWrapper
  "For merging into other structures; Commonly repeated structure"
  {(s/optional-key :scope) Scope})

(def SecurityCompromise
  (s/enum "Yes" "No" "Suspected" "Unknown"))

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

   :type s/Str ;; fixed vocab

   :producer s/Str
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

   (s/optional-key :related_coas) [Reference]



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

(def OwnershipClass
  (s/enum "Internally-Owned"
          "Employee-Owned"
          "Partner-Owned"
          "Customer-Owned"
          "Unkown"))

(def ManagementClass
  (s/enum "Internally-Managed"
          "Externally-Management" ;; SIC
          "CO-Managment"
          "Unkown"))

(def LocationClass
  (s/enum "Internally-Located"
          "Externally-Located"
          "Co-Located"
          "Mobile"
          "Unknown"))

(def LossProperty
  (s/enum "Confidentiality"
          "Integrity"
          "Availability"
          "Accountability"
          "Non-Repudiation"))

(def LossDuration
  (s/enum "Permanent"
          "Weeks"
          "Days"
          "Hours"
          "Minutes"
          "Seconds"
          "Unknown"))

(def SecurityCompromise
  (s/enum "Yes"
          "Suspected"
          "No"
          "Unkown"))

(s/defschema NonPublicDataCompromised
  "See http://stixproject.github.io/data-model/1.2/incident/NonPublicDataCompromisedType/"
  ;; Simplified schema
  {:security_compromise SecurityCompromise
   (s/optional-key :data_encrypted) s/Bool})

(s/defschema PropertyAffected
  "See http://stixproject.github.io/data-model/1.2/incident/PropertyAffectedType/"
  {(s/optional-key :property) LossProperty
   (s/optional-key :description_of_effect) s/Str
   (s/optional-key :type_of_availability_loss) s/Str ;; Vocab is empty
   (s/optional-key :duration_of_availability_loss) LossDuration
   (s/optional-key :non_public_data_compromised) NonPublicDataCompromised})

(s/defschema AffectedAsset
  "See http://stixproject.github.io/data-model/1.2/incident/AffectedAssetType/"
  {(s/optional-key :type) s/Str
   (s/optional-key :description) [s/Str]
   (s/optional-key :ownership_class) OwnershipClass
   (s/optional-key :managment_class) ManagementClass
   (s/optional-key :location_class) LocationClass
   (s/optional-key :property_affected) PropertyAffected ;; Unnested NatureOfSecurityEffect
   (s/optional-key :identifying_observables) [Reference] ;; Points to Observable
   ;; Not Provided: business_function_or_role
   ;; Not Provided: location (undefined/abstract type); Could be [s/Str]
   })

(def ImpactRating
  (s/enum "None"
          "Minor"
          "Moderate"
          "Major"
          "Unknown"))

(s/defschema DirectImpactSummary
  "See http://stixproject.github.io/data-model/1.2/incident/DirectImpactSummaryType/"
  {(s/optional-key :asset_losses) ImpactRating
   (s/optional-key :business_mission_distruption) ImpactRating
   (s/optional-key :response_and_recovery_costs) ImpactRating})

(s/defschema IndirectImpactSummary
  "See http://stixproject.github.io/data-model/1.2/incident/IndirectImpactSummaryType/"
  {(s/optional-key :loss_of_competitive_advantage) SecurityCompromise
   (s/optional-key :brand_and_market_damage) SecurityCompromise
   (s/optional-key :increased_operating_costs) SecurityCompromise
   (s/optional-key :local_and_regulatory_costs) SecurityCompromise})

(s/defschema LossEstimation
  "See http://stixproject.github.io/data-model/1.2/incident/LossEstimationType/"
  {(s/optional-key :amount) s/Num
   (s/optional-key :iso_currency_code) s/Str})

(s/defschema TotalLossEstimation
  "See http://stixproject.github.io/data-model/1.2/incident/TotalLossEstimationType/"
  {(s/optional-key :initial_reported_total_loss_estimation) LossEstimation
   (s/optional-key :actual_total_loss_estimation) LossEstimation})

(def ImpactQualification
  (s/enum "Insignificant"
          "Distracting"
          "Painful"
          "Damaging"
          "Catastrophic"
          "Unknown"))

(def Effect
  (s/enum "Brand or Image Degradation"
          "Loss of Competitive Advantage"
          "Loss of Competitive Advantage - Economic"
          "Loss of Competitive Advantage - Military"
          "Loss of Competitive Advantage - Political"
          "Data Breach or Compromise"
          "Degradation of Service"
          "Destruction"
          "Disruption of Service / Operations"
          "Financial Loss"
          "Loss of Confidential / Proprietary Information or Intellectual Property"
          "Regulatory, Compliance or Legal Impact"
          "Unintended Access"
          "User Data Loss"))

(s/defschema ImpactAssessment
  "See http://stixproject.github.io/data-model/1.2/incident/ImpactAssessmentType/"
  {(s/optional-key :direct_impact_summary) DirectImpactSummary
   (s/optional-key :indirect_impact_summary) IndirectImpactSummary
   (s/optional-key :total_loss_estimation) TotalLossEstimation
   (s/optional-key :impact_qualification) ImpactQualification
   (s/optional-key :effects) [Effect]
   ;; Not provided: external_impact_assessment_model
   })

(s/defschema IncidentTime
  "See http://stixproject.github.io/data-model/1.2/incident/TimeType/"
  {(s/optional-key :first_malicious_action) Time ;; Simplified structure
   (s/optional-key :initial_compromise) Time
   (s/optional-key :first_data_exfiltration) Time
   (s/optional-key :incident_discovery) Time
   (s/optional-key :incident_opened) Time
   (s/optional-key :containment_achieved) Time
   (s/optional-key :restoration_achieved) Time
   (s/optional-key :incident_reported) Time
   (s/optional-key :incident_closed) Time})

(def IncidentCategory
  (s/enum "Exercise/Network Defense Testing"
          "Unauthorized Access"
          "Denial of Service"
          "Malicious Code"
          "Improper Usage"
          "Scans/Probes/Attempted Access"
          "Investigation"))

(s/defschema TimeStructure
  "See http://stixproject.github.io/data-model/1.2/cyboxCommon/TimeType/"
  {(s/optional-key :start_time) Time
   (s/optional-key :end_time) Time
   (s/optional-key :produced_time) Time
   (s/optional-key :received_time) Time})

(def AttackToolType
  "See http://stixproject.github.io/data-model/1.2/stixVocabs/AttackerToolTypeVocab-1.0/"
  (s/enum "Malware"
          "Penetration Testing"
          "Port Scanner"
          "Traffic Scanner"
          "Vulnerability Scanner"
          "Application Scanner"
          "Password Cracking"))

(s/defschema Tool
  "See http://stixproject.github.io/data-model/1.2/cyboxCommon/ToolInformationType/"
  (merge
   GenericStixIdentifiers
   {(s/optional-key :name) s/Str
    (s/optional-key :type) [AttackToolType]
    (s/optional-key :description) s/Str
    (s/optional-key :references) [s/Str]
    (s/optional-key :vendor) s/Str
    (s/optional-key :version) s/Str
    (s/optional-key :service_pack) s/Str
    ;; Not provided: tool_specific_data
    ;; Not provided: tool_hashes
    ;; Not provided: tool_configuration
    ;; Not provided: execution_environment
    ;; Not provided: errors
    ;; Not provided: metadata
    ;; Not provided: compensation_model
    }))

(s/defschema Source
  "See http://stixproject.github.io/data-model/1.2/stixCommon/InformationSourceType/"
  {(s/optional-key :description) s/Str
   (s/optional-key :idntity) s/Str ;; greatly simplified
   (s/optional-key :role) s/Str ;; empty vocab
   (s/optional-key :contributing_sources) [Reference] ;; more Source's
   (s/optional-key :time) TimeStructure
   (s/optional-key :tools) [Tool]
   ;; Not provided: references
   })

(def Status
  (s/enum "New"
          "Open"
          "Stalled"
          "Containment Achieved"
          "Restoration Achieved"
          "Incident Reported"
          "Closed"
          "Rejected"
          "Deleted"))

(def IntendedEffect
  (s/enum "Advantage"
          "Advantage - Economic"
          "Advantage - Military"
          "Advantage - Political"
          "Theft"
          "Theft - Intellectual Property"
          "Theft - Credential Theft"
          "Theft - Identity Theft"
          "Theft - Theft of Proprietary Information"
          "Account Takeover"
          "Brand Damage"
          "Competitive Advantage"
          "Degradation of Service"
          "Denial and Deception"
          "Destruction"
          "Disruption"
          "Embarrassment"
          "Exposure"
          "Extortion"
          "Fraud"
          "Harassment"
          "ICS Control"
          "Traffic Diversion"
          "Unauthorized Access"))

(def DiscoveryMethod
  (s/enum "Agent Disclosure"
          "External - Fraud Detection"
          "Monitoring Service"
          "Law Enforcement"
          "Customer"
          "Unrelated Party"
          "Audit"
          "Antivirus"
          "Incident Response"
          "Financial Audit"
          "Internal - Fraud Detection"
          "HIPS"
          "IT Audit"
          "Log Review"
          "NIDS"
          "Security Alarm"
          "User"
          "Unknown"))

(s/defschema History
  "See http://stixproject.github.io/data-model/1.2/incident/HistoryItemType/"
  {(s/optional-key :action_entry) Reference ;; COA
   (s/optional-key :journal_entry) s/Str ;; simplified
   })

(def MalwareType
  (s/enum "Automated Transfer Scripts"
          "Adware"
          "Dialer"
          "Bot"
          "Bot - Credential Theft"
          "Bot - DDoS"
          "Bot - Loader"
          "Bot - Spam"
          "DoS/ DDoS"
          "DoS / DDoS - Participatory"
          "DoS / DDoS - Script"
          "DoS / DDoS - Stress Test Tools"
          "Exploit Kit"
          "POS / ATM Malware"
          "Ransomware"
          "Remote Access Trojan"
          "Rogue Antivirus"
          "Rootkit"))

(s/defschema AttackPattern
  "See http://stixproject.github.io/data-model/1.2/ttp/AttackPatternType/"
  (merge
   GenericStixIdentifiers
   {(s/optional-key :capec_id) s/Str}))

(s/defschema MalwareInstance
  "See http://stixproject.github.io/data-model/1.2/ttp/MalwareInstanceType/"
  (merge
   GenericStixIdentifiers
   {(s/optional-key :type) [MalwareType]
    ;; Not provided: name ; empty vocab
    }))

(s/defschema Behavior
  "See http://stixproject.github.io/data-model/1.2/ttp/BehaviorType/"
  {(s/optional-key :attack_patterns) [AttackPattern]
   (s/optional-key :malware_type) [MalwareInstance]
   ;; Not provided: exploits ; It is abstract
   })

(def AttackerInfrastructure
  "See http://stixproject.github.io/data-model/1.2/stixVocabs/AttackerInfrastructureTypeVocab-1.0/"
  (s/enum "Anonymization"
          "Anonymization - Proxy"
          "Anonymization - TOR Network"
          "Anonymization - VPN"
          "Communications"
          "Communications - Blogs"
          "Communications - Forums"
          "Communications - Internet Relay Chat"
          "Communications - Micro-Blogs"
          "Communications - Mobile Communications"
          "Communications - Social Networks"
          "Communications - User-Generated Content Websites"
          "Domain Registration"
          "Domain Registration - Dynamic DNS Services"
          "Domain Registration - Legitimate Domain Registration Services"
          "Domain Registration - Malicious Domain Registrars"
          "Domain Registration - Top-Level Domain Registrars"
          "Hosting"
          "Hosting - Bulletproof / Rogue Hosting"
          "Hosting - Cloud Hosting"
          "Hosting - Compromised Server"
          "Hosting - Fast Flux Botnet Hosting"
          "Hosting - Legitimate Hosting"
          "Electronic Payment Methods"))

(s/defschema Infrastructure
  "See http://stixproject.github.io/data-model/1.2/ttp/InfrastructureType/"
  (merge
   GenericStixIdentifiers
   {:type AttackerInfrastructure
    ;; Not provided: observable_characterization ; characterization of CybOX observables
    }))

(s/defschema RelatedIdentity
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedIdentityType/"
  {(s/optional-key :confidence) Confidence
   (s/optional-key :information_source) Source
   (s/optional-key :relationship) s/Str ;; empty vocab
   :identity Reference ;; Points to Identity
   })

(s/defschema Identity
  "See http://stixproject.github.io/data-model/1.2/stixCommon/IdentityType/"
  (merge
   MinimalStixIdentifiers
   {:name s/Str
    :related_identities [RelatedIdentity]}))

(s/defschema Resource
  "See http://stixproject.github.io/data-model/1.2/ttp/ResourceType/"
  {(s/optional-key :tools) [Tool]
   (s/optional-key :infrastructure) Infrastructure
   (s/optional-key :providers) [Identity]})

(def SystemType
  "See http://stixproject.github.io/data-model/1.2/stixVocabs/SystemTypeVocab-1.0/"
  (s/enum "Enterprise Systems"
          "Enterprise Systems - Application Layer"
          "Enterprise Systems - Database Layer"
          "Enterprise Systems - Enterprise Technologies and Support Infrastructure"
          "Enterprise Systems - Network Systems"
          "Enterprise Systems - Networking Devices"
          "Enterprise Systems - Web Layer"
          "Enterprise Systems - VoIP"
          "Industrial Control Systems"
          "Industrial Control Systems - Equipment Under Control"
          "Industrial Control Systems - Operations Management"
          "Industrial Control Systems - Safety, Protection and Local Control"
          "Industrial Control Systems - Supervisory Control"
          "Mobile Systems"
          "Mobile Systems - Mobile Operating Systems"
          "Mobile Systems - Near Field Communications"
          "Mobile Systems - Mobile Devices"
          "Third-Party Services"
          "Third-Party Services - Application Stores"
          "Third-Party Services - Cloud Services"
          "Third-Party Services - Security Vendors"
          "Third-Party Services - Social Media"
          "Third-Party Services - Software Update"
          "Users"
          "Users - Application And Software"
          "Users - Workstation"
          "Users - Removable Media"))

(def InformationType
  "See http://stixproject.github.io/data-model/1.2/stixVocabs/InformationTypeVocab-1.0/"
  (s/enum "Information Assets"
          "Information Assets - Corporate Employee Information"
          "Information Assets - Customer PII"
          "Information Assets - Email Lists / Archives"
          "Information Assets - Financial Data"
          "Information Assets - Intellectual Property"
          "Information Assets - Mobile Phone Contacts"
          "Information Assets - User Credentials"
          "Authentication Cookies"))

(s/defschema VictimTargeting
  "See http://stixproject.github.io/data-model/1.2/ttp/VictimTargetingType/"
  {(s/optional-key :identity) Identity
   (s/optional-key :targeted_systems) [SystemType]
   (s/optional-key :targeted_information) [InformationType]
   ;; Not provided: targeted_technical_details ; Points to ObservablesType
   })

(s/defschema Vulnerability
  "See http://stixproject.github.io/data-model/1.2/et/VulnerabilityType/"
  {(s/optional-key :is_known) s/Bool
   (s/optional-key :is_public_acknowledged) s/Bool
   :title s/Str
   :description [s/Str]
   (s/optional-key :short_description) [s/Str]
   (s/optional-key :cve_id) s/Str
   (s/optional-key :osvdb_id) s/Int
   (s/optional-key :source) s/Str ; source of CVE or OSVDB ref
   (s/optional-key :discovered_datetime) Time ; Simplified
   (s/optional-key :published_datetime) Time ; Simplified
   ;; TODO - :affected_software below is greatly simplified, should it be expanded?
   (s/optional-key :affected_software) [s/Str]
   (s/optional-key :references) [URI]
   ;; Not provided: CVSS_Score ; Should it be?
   })

(s/defschema Weakness
  "See http://stixproject.github.io/data-model/1.2/et/WeaknessType/"
  {:description [s/Str]
   (s/optional-key :cwe_id) s/Str ;; CWE identifier for a particular weakness
   })

(s/defschema Configuration
  "See http://stixproject.github.io/data-model/1.2/et/ConfigurationType/"
  {:description [s/Str]
   (s/optional-key :short_description) [s/Str]
   (s/optional-key :cce_id) s/Str ;; The CCE identifier for a configuration item
   })

(def ThreatActorType
  (s/enum "Cyber Espionage Operations"
          "Hacker"
          "Hacker - White hat"
          "Hacker - Gray hat"
          "Hacker - Black hat"
          "Hacktivist"
          "State Actor / Agency"
          "eCrime Actor - Credential Theft Botnet Operator"
          "eCrime Actor - Credential Theft Botnet Service"
          "eCrime Actor - Malware Developer"
          "eCrime Actor - Money Laundering Network"
          "eCrime Actor - Organized Crime Actor"
          "eCrime Actor - Spam Service"
          "eCrime Actor - Traffic Service"
          "eCrime Actor - Underground Call Service"
          "Insider Threat"
          "Disgruntled Customer / User"))

(def Motivation
  (s/enum "Ideological"
          "Ideological - Anti-Corruption"
          "Ideological - Anti-Establishment"
          "Ideological - Environmental"
          "Ideological - Ethnic / Nationalist"
          "Ideological - Information Freedom"
          "Ideological - Religious"
          "Ideological - Security Awareness"
          "Ideological - Human Rights"
          "Ego"
          "Financial or Economic"
          "Military"
          "Opportunistic"
          "Political"))

(def Sophistication
  (s/enum "Innovator"
          "Expert"
          "Practitioner"
          "Novice"
          "Aspirant"))


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
