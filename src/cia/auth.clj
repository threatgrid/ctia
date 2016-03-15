(ns cia.auth)

(defprotocol IIdentity
  (login [this])
  (allowed-capabilities [this])
  (allowed-capability? [this capability]))

(defprotocol IAuth
  (require-login? [this])
  (identity-for-token [this token]))

(defonce auth-service (atom nil))

(def default-capabilities
  {:user
   #{:read-judgement
     :list-judgements-by-observable
     :list-judgements-by-indicator
     :read-indicator
     :list-indicators-by-title
     :read-feedback
     :reat-ttp
     :read-campaign
     :read-actor
     :read-exploit-target
     :read-coa
     :read-sighting
     :read-incident
     :read-relation}
   :admin
   #{:admin}})
