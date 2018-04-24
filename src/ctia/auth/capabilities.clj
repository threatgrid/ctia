(ns ctia.auth.capabilities
  (:require
   [ctia.entity.entities :refer [entities]]))

(def all-capabilities
  (apply clojure.set/union
         #{:read-verdict
           ;; Other
           :developer
           :specify-id
           :external-id
           :import-bundle}
         (keep (fn [[_ entity]]
                 (:capabilities entity)) entities)))

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
     :read-sighting
     :list-sightings
     :read-tool
     :read-verdict}
   :admin
   all-capabilities})
