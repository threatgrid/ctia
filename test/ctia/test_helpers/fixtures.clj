(ns ctia.test-helpers.fixtures
  (:require [ctim.domain.id :refer [make-transient-id]]
            [ctim.examples
             [actors :refer [actor-maximal actor-minimal]]
             [attack-patterns :refer [attack-pattern-maximal attack-pattern-minimal]]
             [campaigns :refer [campaign-maximal campaign-minimal]]
             [coas :refer [coa-maximal coa-minimal]]
             [incidents :refer [incident-maximal incident-minimal]]
             [indicators :refer [indicator-maximal indicator-minimal]]
             [investigations :refer [investigation-maximal investigation-minimal]]
             [judgements :refer [judgement-maximal judgement-minimal]]
             [malwares :refer [malware-maximal malware-minimal]]
             [relationships :refer [relationship-maximal relationship-minimal]]
             [casebooks :refer [casebook-maximal casebook-minimal]]
             [sightings :refer [sighting-maximal sighting-minimal]]
             [identity-assertions :refer [identity-assertion-maximal identity-assertion-minimal]]
             [tools :refer [tool-maximal tool-minimal]]
             [vulnerabilities :refer [vulnerability-maximal vulnerability-minimal]]
             [weaknesses :refer [weakness-maximal weakness-minimal]]]))

(defn randomize [doc]
  (assoc doc
         :id (make-transient-id "_")))

(defn n-doc [fixture nb]
  (map randomize (repeat nb fixture)))

(defn n-examples
  [entity-type nb maximal?]
  (case entity-type
    :actor (n-doc (if maximal? actor-maximal actor-minimal) nb)
    :attack-pattern (n-doc (if maximal? attack-pattern-maximal attack-pattern-minimal) nb)
    :campaign (n-doc (if maximal? campaign-maximal campaign-minimal) nb)
    :coa (n-doc (if maximal? coa-maximal coa-minimal) nb)
    :incident (n-doc (if maximal? incident-maximal incident-minimal) nb)
    :indicator (n-doc (if maximal? indicator-maximal indicator-minimal) nb)
    :investigation (n-doc (if maximal? investigation-maximal investigation-minimal) nb)
    :judgement (n-doc (if maximal? judgement-maximal judgement-minimal) nb)
    :malware (n-doc (if maximal? malware-maximal malware-minimal) nb)
    :relationship (n-doc (if maximal? relationship-maximal relationship-minimal) nb)
    :casebook (n-doc (if maximal? casebook-maximal casebook-minimal) nb)
    :sighting (n-doc (if maximal? sighting-maximal sighting-minimal) nb)
    :tool (n-doc (if maximal? tool-maximal tool-minimal) nb)
    :vulnerability (n-doc (if maximal? vulnerability-maximal vulnerability-minimal) nb)
    :weakness (n-doc (if maximal? weakness-maximal weakness-minimal) nb)))

(defn bundle
  [fixtures-nb maximal?]
  {:actors (n-examples :actor fixtures-nb maximal?)
   :attack_patterns (n-examples :attack-pattern fixtures-nb maximal?)
   :campaigns (n-examples :campaign fixtures-nb maximal?)
   :coas (n-examples :coa fixtures-nb maximal?)
   :incidents (n-examples :incident fixtures-nb maximal?)
   :indicators (n-examples :indicator fixtures-nb maximal?)
   :investigations (n-examples :investigation fixtures-nb maximal?)
   :judgements (n-examples :judgement fixtures-nb maximal?)
   :malwares (n-examples :malware fixtures-nb maximal?)
   :relationships (n-examples :relationship fixtures-nb maximal?)
   :casebooks (n-examples :casebook fixtures-nb maximal?)
   :sightings (n-examples :sighting fixtures-nb maximal?)
   :tools (n-examples :tool fixtures-nb maximal?)
   :vulnerabilities (n-examples :vulnerability fixtures-nb maximal?)
   :weaknesses (n-examples :weakness fixtures-nb maximal?)})
