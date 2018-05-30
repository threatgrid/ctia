(ns ctia.auth.capabilities
  (:require [clojure.set :as set]))

(def entities
  #{:actor
    :attack-pattern
    :campaign
    :casebook
    :coa
    :data-table
    :exploit-target
    :feedback
    :incident
    :indicator
    :investigation
    :judgement
    :malware
    :relationship
    :sighting
    :tool
    :verdict})

(def prefixes
  {:read #{:read :search :list}
   :write #{:create :delete}})

(defn gen-capabilities-for-entity-and-accesses
  "Given an entity and a set of access (:read or :write) generate a set of
  capabilities"
  [entity-name accesses]
  (set (for [access accesses
             prefix (get prefixes access)]
         (keyword (str (name prefix) "-" (name entity-name)
                       (if (= :list prefix) "s" ""))))))

(def all-entity-capabilities
  (apply set/union
         (map #(gen-capabilities-for-entity-and-accesses
                % (keys prefixes)) entities)))

(def all-capabilities
  (set/union
   #{:read-verdict
     ;; Other
     :developer
     :specify-id
     :external-id
     :import-bundle}
   all-entity-capabilities))

(def default-capabilities
  {:user
   #{:read-actor
     :read-attack-pattern
     :read-campaign
     :read-coa
     :read-exploit-target
     :read-feedback
     :read-incident
     :read-indicator
     :list-indicators
     :read-judgement
     :list-judgements
     :read-malware
     :read-relationship
     :list-relationships
     :read-sighting
     :list-sightings
     :read-tool
     :read-verdict}
   :admin
   all-capabilities})
