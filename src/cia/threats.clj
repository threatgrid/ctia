(ns cia.threats
  (:require [schema.core :as s]
            [cia.models :refer [Observable ObservableType Time URI Confidence Reference ID]]
            [ring.swagger.schema :refer [coerce!]]))
;;mutable
(s/defschema TTP
  "See http://stixproject.github.io/data-model/1.2/ttp/TTPType/"
  {:id ID
   :title s/Str
   :source s/Str
   :type  s/Str
   :timestamp Time
   :expires Time

   :description s/Str
   :short_description s/Str 

   :intended_effect s/Str ;; typed

   :behavior s/Str ;;typed

   :indicators [Reference]
   })

;;mutable
(s/defschema Actor
  "http://stixproject.github.io/data-model/1.2/ta/ThreatActorType/"
  {:id ID
   :title s/Str
   :source s/Str
   :type  s/Str
   :timestamp Time
   :expires Time

   :description s/Str
   :short_description s/Str 
   })

;;mutable
(s/defschema Campaign
  "See http://stixproject.github.io/data-model/1.2/campaign/CampaignType/"
  {:id ID
   :title s/Str
   :source s/Str
   :type  s/Str
   :timestamp Time
   :expires Time
   
   :description s/Str
   :short_description s/Str

   :indicators [Reference]

   })

(s/defschema COA
  {:id ID
   :title s/Str
   :stage s/Str ;;fixed vocab
   :type s/Str
   :short_description s/Str
   :description s/Str

   :objective [s/Str]

   :impact s/Str
   :cost s/Str
   :effidacy s/Str

   :source s/Str

   :handling s/Str
   :related_COAs [Reference]
   
   })
