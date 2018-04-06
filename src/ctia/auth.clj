(ns ctia.auth
  (:require [schema.core :as s]))

(defprotocol IIdentity
  (authenticated? [this])
  (login [this])
  (groups [this])
  (allowed-capabilities [this])
  (capable? [this capabilities]))

(defprotocol IAuth
  (identity-for-token [this token]))

(defonce auth-service (atom nil))

(def all-capabilities
  #{;; Actor
    :create-actor
    :read-actor
    :delete-actor
    :search-actor

    ;; Attack Pattern
    :create-attack-pattern
    :read-attack-pattern
    :delete-attack-pattern
    :search-attack-pattern

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
    :search-data-table

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
    :delete-indicator

    ;; Investigation
    :read-investigation
    :list-investigations
    :create-investigation
    :search-investigation
    :delete-investigation

    ;; Judgement
    :create-judgement
    :read-judgement
    :list-judgements
    :delete-judgement
    :search-judgement

    ;; Malware
    :create-malware
    :read-malware
    :delete-malware
    :search-malware

    ;; Relationship
    :create-relationship
    :read-relationship
    :list-relationships
    :delete-relationship
    :search-relationship

    ;; Casebook
    :create-casebook
    :read-casebook
    :list-casebooks
    :delete-casebook
    :search-casebook

    ;; Sighting
    :create-sighting
    :read-sighting
    :list-sightings
    :delete-sighting
    :search-sighting

    ;; Tool
    :create-tool
    :read-tool
    :delete-tool
    :search-tool

    ;; Verdict
    :read-verdict

    ;; Other
    :developer
    :specify-id
    :external-id
    :import-bundle})

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

(def not-logged-in-owner "Unknown")

(def not-logged-in-groups ["Unknown Group"])

(def admingroup "Administrators")

(defrecord DeniedIdentity []
  IIdentity
  (authenticated? [_]
    false)
  (login [_]
    not-logged-in-owner)
  (groups [_]
    not-logged-in-groups)
  (allowed-capabilities [_]
    #{})
  (capable? [_ _]
    false))

(def denied-identity-singleton (->DeniedIdentity))

(s/defn ident->map :- (s/maybe {:login (s/maybe s/Str)
                                :groups (s/maybe [s/Str])})
  [ident]
  (when ident
    {:login (login ident)
     :groups (groups ident)}))
