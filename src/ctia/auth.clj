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
    :delete-actor
    :search-actor

    ;; Campaign
    :create-campaign
    :read-campaign
    :delete-campaign
    :search-campaign

    ;; COA
    :create-coa
    :read-coa
    :delete-coa
    :search-coa

    ;; Data-Table
    :create-data-table
    :read-data-table
    :delete-data-table

    ;; Exploit-Target
    :create-exploit-target
    :read-exploit-target
    :delete-exploit-target
    :search-exploit-target

    ;; Feedback
    :create-feedback
    :read-feedback
    :delete-feedback

    ;; Incident
    :create-incident
    :read-incident
    :delete-incident
    :search-incident

    ;; Indicator
    :read-indicator
    :list-indicators
    :create-indicator
    :search-indicator

    ;; Judgement
    :create-judgement
    :read-judgement
    :list-judgements
    :delete-judgement
    :search-judgement

    ;; Relationship
    :create-relationship
    :read-relationship
    :list-relationships
    :delete-relationship
    :search-relationship

    ;; Sighting
    :create-sighting
    :read-sighting
    :list-sightings
    :delete-sighting
    :search-sighting

    ;; TTP
    :create-ttp
    :read-ttp
    :delete-ttp
    :search-ttp

    ;; Verdict
    :read-verdict

    ;; Bundle
    :create-bundle
    :read-bundle
    :delete-bundle

    ;; Other
    :developer
    :specify-id
    :external-id})

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
