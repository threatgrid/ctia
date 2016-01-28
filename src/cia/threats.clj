(ns cia.threats
  (:require [schema.core :as s]
            [cia.models :refer [Observable Time URI Confidence Reference ID Indicator]]
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

(s/defschema Incident
  "See http://stixproject.github.io/data-model/1.2/incident/IncidentType/"
  {:id ID
   :timestamp Time

   :description s/Str
   :short_description s/Str
   :status s/Str

   (s/optional-key :categories) [s/Str]
   (s/optional-key :reporter) s/Str
   (s/optional-key :responder) s/Str
   (s/optional-key :coordinator) s/Str
   (s/optional-key :victim) s/Str

   ;; affected assets ?
   ;; impact assessment ?

   ;; The seqs of elements below are squashed (they leave out
   ;; structured data such as confidence and source for each element).
   (s/optional-key :related_indicators) {:scope Scope
                                         :indicators [Reference]}
   (s/optional-key :related_observables) {:scope Scope
                                          :observables [Reference]}
   (s/optional-key :leveraged_ttps) {:scope Scope
                                     :ttps [Reference]}
   (s/optional-key :attributed_actors) {:scope Scope
                                        :actors [Reference]}
   (s/optional-key :related_incidents) {:scope Scope
                                        :incidents [Reference]}
   (s/optional-key :intended_effect) s/Str ;; typed?

   :security_compromise SecurityCompromise
   :discover_method s/Str ;; typed?

   :coa_requested [Reference]
   :coa_taken [Reference]

   :confidence s/Str ;; typed?

   ;; contact?
   ;; history?
   ;; information source?
   ;; handling?
   })
