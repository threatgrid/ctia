(ns ctia.task.migration.fixtures
  (:require [ctim.domain.id :refer [make-transient-id]]
            [ctim.examples
             [actors :refer [actor-minimal]]
             [attack-patterns :refer [attack-pattern-minimal]]
             [campaigns :refer [campaign-minimal]]
             [coas :refer [coa-minimal]]
             [incidents :refer [incident-minimal]]
             [indicators :refer [indicator-minimal]]
             [investigations :refer [investigation-minimal]]
             [judgements :refer [judgement-minimal]]
             [malwares :refer [malware-minimal]]
             [relationships :refer [relationship-minimal]]
             [casebooks :refer [casebook-minimal]]
             [sightings :refer [sighting-minimal]]
             [tools :refer [tool-minimal]]
             [vulnerabilities :refer [vulnerability-minimal]]
             [weaknesses :refer [weakness-minimal]]]))

(def fixtures-nb 100)

(defn randomize [doc]
  (assoc doc
         :id (make-transient-id "_")))

(defn n-doc [fixture nb]
  (map randomize (repeat nb fixture)))

(def examples
  {:actors (n-doc actor-minimal fixtures-nb)
   :attack_patterns (n-doc attack-pattern-minimal fixtures-nb)
   :campaigns (n-doc campaign-minimal fixtures-nb)
   :coas (n-doc coa-minimal fixtures-nb)
   :incidents (n-doc incident-minimal fixtures-nb)
   :indicators (n-doc indicator-minimal fixtures-nb)
   :investigations (n-doc investigation-minimal fixtures-nb)
   :judgements (n-doc judgement-minimal fixtures-nb)
   :malwares (n-doc malware-minimal fixtures-nb)
   :relationships (n-doc relationship-minimal fixtures-nb)
   :casebooks (n-doc casebook-minimal fixtures-nb)
   :sightings (n-doc sighting-minimal fixtures-nb)
   :tools (n-doc tool-minimal fixtures-nb)
   :vulnerabilities (n-doc vulnerability-minimal fixtures-nb)
   :weaknesses (n-doc weakness-minimal fixtures-nb)})

(def example-types
  (->> (vals examples)
       (map #(-> % first :type keyword))
       set))
