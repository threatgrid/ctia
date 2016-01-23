(ns cia.threats
  (:require [schema.core :as s]
            [cia.models :refer [Observable ObservableType Time URI Confidence]]
            [ring.swagger.schema :refer [coerce!]]))
;;mutable
(s/defschema TTP
  "See http://stixproject.github.io/data-model/1.2/ttp/TTPType/"
  {:id s/Str
   :title s/Str
   :origin s/Str
   :type  s/Str
   :timestamp Time
   :expires Time

   :description s/Str
   :short_description s/Str 

   :intended_effect s/Str ;; typed

   :behavior s/Str ;;typed
   })

;;mutable
(s/defschema Actor
  "http://stixproject.github.io/data-model/1.2/ta/ThreatActorType/"
  {:id s/Str
   :title s/Str
   :origin s/Str
   :type  s/Str
   :timestamp Time
   :expires Time

   :description s/Str
   :short_description s/Str 
   })

;;mutable
(s/defschema Campaign
  "See http://stixproject.github.io/data-model/1.2/campaign/CampaignType/"
  {:id s/Str
   :title s/Str
   :origin s/Str
   :type  s/Str
   :timestamp Time
   :expires Time
   
   :description s/Str
   :short_description s/Str 
   })

