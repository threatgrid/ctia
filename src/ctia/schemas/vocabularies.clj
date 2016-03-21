(ns ctia.schemas.vocabularies
  (:require [schema.core :as s]))

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

(def AttackToolType
  "See http://stixproject.github.io/data-model/1.2/stixVocabs/AttackerToolTypeVocab-1.0/"
  (s/enum "Malware"
          "Penetration Testing"
          "Port Scanner"
          "Traffic Scanner"
          "Vulnerability Scanner"
          "Application Scanner"
          "Password Cracking"))

(def CampaignStatus
  (s/enum "Ongoing"
          "Historic"
          "Future"))

(def COAStage
  "See http://stixproject.github.io/data-model/1.2/stixVocabs/COAStageVocab-1.0/"
  (s/enum "Remedy"
          "Response"))

(def COAType
  "See http://stixproject.github.io/data-model/1.2/stixVocabs/CourseOfActionTypeVocab-1.0/"
  (s/enum "Perimeter Blocking"
          "Internal Blocking"
          "Redirection"
          "Redirection (Honey Pot)"
          "Hardening"
          "Patching"
          "Eradication"
          "Rebuilding"
          "Training"
          "Monitoring"
          "Physical Access Restrictions"
          "Logical Access Restrictions"
          "Public Disclosure"
          "Diplomatic Actions"
          "Policy Actions"
          "Other"))

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

(def HighMedLow
  "See http://stixproject.github.io/data-model/1.2/stixVocabs/HighMediumLowVocab-1.0/"
  (s/enum "Low"
          "Medium"
          "High"
          "None"
          "Unknown"))

(def ImpactQualification
  (s/enum "Insignificant"
          "Distracting"
          "Painful"
          "Damaging"
          "Catastrophic"
          "Unknown"))

(def ImpactRating
  (s/enum "None"
          "Minor"
          "Moderate"
          "Major"
          "Unknown"))

(def IncidentCategory
  (s/enum "Exercise/Network Defense Testing"
          "Unauthorized Access"
          "Denial of Service"
          "Malicious Code"
          "Improper Usage"
          "Scans/Probes/Attempted Access"
          "Investigation"))

(def IndicatorType
  "See http://stixproject.github.io/data-model/1.2/stixVocabs/IndicatorTypeVocab-1.1/"
  (s/enum "Malicious E-mail"
          "IP Watchlist"
          "File Hash Watchlist"
          "Domain Watchlist"
          "URL Watchlist"
          "Malware Artifacts"
          "C2"
          "Anonymization"
          "Exfiltration"
          "Host Characteristics"
          "Compromised PKI Certificate"
          "Login Name"
          "IMEI Watchlist"
          "IMSI Watchlist"))

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

(def LocationClass
  (s/enum "Internally-Located"
          "Externally-Located"
          "Co-Located"
          "Mobile"
          "Unknown"))

(def LossDuration
  (s/enum "Permanent"
          "Weeks"
          "Days"
          "Hours"
          "Minutes"
          "Seconds"
          "Unknown"))

(def LossProperty
  (s/enum "Confidentiality"
          "Integrity"
          "Availability"
          "Accountability"
          "Non-Repudiation"))

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

(def ManagementClass
  (s/enum "Internally-Managed"
          "Externally-Management" ;; SIC
          "CO-Managment"
          "Unkown"))

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

(def ObservableType
  "Observable type names"
  (s/enum "ip"
          "ipv6"
          "device" ;; Was "mac"
          "user"
          "domain"
          "sha256"
          "md5"
          "sha1"
          "url"))

(def OwnershipClass
  (s/enum "Internally-Owned"
          "Employee-Owned"
          "Partner-Owned"
          "Customer-Owned"
          "Unknown"))

(def Scope
  "Not a defined vocab, this enum is commonly repeated"
  (s/enum "inclusive"
          "exclusive"))

(def SecurityCompromise
  (s/enum "Yes"
          "Suspected"
          "No"
          "Unknown"))

(def Sophistication
  (s/enum "Innovator"
          "Expert"
          "Practitioner"
          "Novice"
          "Aspirant"))

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
