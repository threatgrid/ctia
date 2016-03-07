(ns cia.auth)

(defprotocol IIdentity
  (login [this])
  (allowed-capabilities [this])
  (allowed-capability? [this capability]))

(defprotocol IAuth
  (identity-for-token [this token]))

(defonce auth-service (atom nil))

(def default-capabilities
  {:user
   #{:read-judgement
     :list-judgements-by-observable
     :list-judgements-by-indicator
     :read-indicator
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
