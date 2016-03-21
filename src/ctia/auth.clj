(ns ctia.auth)

(defprotocol IIdentity
  (authenticated? [this])
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

(def not-logged-in-owner "Unknown")

(defrecord DeniedIdentity []
  IIdentity
  (authenticated? [_]
    false)
  (login [_]
    not-logged-in-owner)
  (allowed-capabilities [_]
    #{})
  (allowed-capability? [_ _]
    false))

(def denied-identity-singleton (->DeniedIdentity))
