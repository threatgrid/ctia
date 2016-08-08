(ns ctia.auth)

(defprotocol IIdentity
  (authenticated? [this])
  (login [this])
  (allowed-capabilities [this])
  (capable? [this capabilities]))

(defprotocol IAuth
  (require-login? [this])
  (identity-for-token [this token]))

(defonce auth-service (atom nil))

(def all-capabilities
  #{;; Actor
    :create-actor
    :read-actor
    :list-actors-by-external-id
    :delete-actor

    ;; Campaign
    :create-campaign
    :read-campaign
    :list-campaigns-by-external-id
    :delete-campaign

    ;; COA
    :create-coa
    :read-coa
    :list-coas-by-external-id
    :delete-coa

    ;; Exploit-Target
    :create-exploit-target
    :read-exploit-target
    :list-exploit-targets-by-external-id
    :delete-exploit-target

    ;; Feedback
    :create-feedback
    :read-feedback
    :list-feedback-by-external-id
    :delete-feedback

    ;; Incident
    :create-incident
    :read-incident
    :list-incidents-by-external-id
    :delete-incident

    ;; Indicator
    :read-indicator
    :list-indicators
    :list-indicators-by-external-id
    :create-indicator

    ;; Judgement
    :create-judgement
    :read-judgement
    :list-judgements-by-external-id
    :list-judgements
    :delete-judgement

    ;; Sighting
    :create-sighting
    :read-sighting
    :list-sightings
    :list-sightings-by-external-id
    :delete-sighting

    ;; TTP
    :create-ttp
    :read-ttp
    :list-ttps-by-external-id
    :delete-ttp

    ;; Verdict
    :read-verdict

    ;; Other
    :developer
    :specify-id})

(def default-capabilities
  {:user
   #{:read-actor
     :read-campaign
     :read-coa
     :read-exploit-target
     :read-feedback
     :read-incident
     :read-indicator
     :list-indicators
     :read-judgement
     :list-judgements
     :read-sighting
     :list-sightings
     :read-ttp
     :read-verdict}
   :admin
   all-capabilities})

(def not-logged-in-owner "Unknown")

(defrecord DeniedIdentity []
  IIdentity
  (authenticated? [_]
    false)
  (login [_]
    not-logged-in-owner)
  (allowed-capabilities [_]
    #{})
  (capable? [_ _]
    false))

(def denied-identity-singleton (->DeniedIdentity))
