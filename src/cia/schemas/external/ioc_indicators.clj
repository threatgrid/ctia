(ns cia.schemas.external.ioc-indicators
  (:require [schema.core :as s]))

(s/defschema IoCIndicator
  {(s/required-key "confidence") s/Int
   (s/required-key "author") s/Str
   (s/required-key "tags") [s/Str]
   (s/required-key "name") s/Str
   (s/required-key "created-at") s/Str
   (s/required-key "variables") [s/Str]
   (s/required-key "title") s/Str
   (s/required-key "last-modified") s/Str
   (s/required-key "category") [s/Str]
   (s/required-key "severity") s/Int
   (s/required-key "description") s/Str})

(s/defschema IoCTag
  (s/enum "ActionScript"
          "addon"
          "adware"
          "artifact"
          "attributes"
          "anomaly"
          "autorun"
          "banker"
          "browser"
          "botnet"
          "command and control"
          "communication"
          "crash"
          "create"
          "crypter"
          "DLL"
          "DLL Hijacking"
          "decoy"
          "dns"
          "dropper"
          "error"
          "embedded content"
          "encoding"
          "enumeration"
          "execute"
          "executable"
          "exfiltration"
          "file"
          "firewall"
          "Flash"
          "fraud"
          "geoip"
          "hijack"
          "hijacking"
          "http"
          "host"
          "IP"
          "ip address"
          "launch"
          "Launch"
          "legitimate"
          "library"
          "location"
          "lock"
          "malware"
          "mutex"
          "network"
          "Network_Stream"
          "obfuscation"
          "PDF"
          "phishing"
          "packer"
          "PDF"
          "PE"
          "port"
          "Port"
          "process"
          "protocol"
          "Protocol"
          "p2p"
          "ransomware"
          "RAT"
          "recon"
          "recycler"
          "redirect"
          "registry"
          "remote control"
          "resource"
          "rootkit"
          "scareware"
          "script"
          "service"
          "Social-Engineering"
          "spyware"
          "suspicious"
          "Stream"
          "system"
          "system enumeration"
          "system modification"
          "static"
          "threshold"
          "tracking"
          "trojan"
          "Trojan IsSpace detected"
          "ZeroAccess"
          "zeus"))

(s/defschema IoCVariable
  (s/enum "Action_Target"
          "Action_Type"
          "ahash"
          "Antivirus_Product"
          "Antivirus_Result"
          "artifact"
          "Artifact_ID"
          "Artifact_Hash"
          "Arguments"
          "Command"
          "Command_Line"
          "File_Type"
          "IP"
          "IP_Addr"
          "issuer"
          "Is_Dot_Net_EXE"
          "Lang"
          "magic_type"
          "Method"
          "Mutant_Name"
          "Network_Stream"
          "offset"
          "Path"
          "Port"
          "Process_ID"
          "Process_Name"
          "Protocol"
          "Query_Data"
          "Query_ID"
          "Reference"
          "RegKey_Data"
          "RegKey_Data_Type"
          "RegKey_Name"
          "RegKey_Value"
          "RegKey_Value_Name"
          "rid"
          "secName"
          "Section_Type"
          "serial"
          "sha1"
          "Size"
          "subject"
          "Subsystem"
          "Sub_Lang"
          "Target"
          "timestamp"
          "URL"
          "Window_Display"))

(s/defschema IoCCategory
  (s/enum "attribute"
          "compound"
          "enumeration"
          "evasion"
          "file"
          "forensics"
          "malware"
          "network"
          "persistence"
          "weakening"))
