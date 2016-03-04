(ns cia.schemas.auth-role
  (:require [cia.schemas.common :as c]
            [schema.core :as s]
            [schema-tools.core :as st]))

(def capabilities
  [:get-verdict

   ;; judgements
   :create-judgement
   :read-judgement
   :delete-judgement
   :list-judgements-by-observable
   :list-judgements-by-indicator

   ;; indicators
   :create-indicator
   :read-indicator
   ;;:read-indicator-implementation
   :delete-indicator
   :list-indicators

   ;; feedback
   :create-feedback
   :read-feedback


   ;; threats
   :create-ttp
   :delete-ttp
   :read-ttp
   :list-ttps

   :create-campaign
   :delete-campaign
   :read-campaign
   :list-campaigns

   :create-actor
   :delete-actor
   :read-actor
   :list-actors

   :create-exploit-target
   :read-exploit-target
   :delete-exploit-target
   :list-exploit-targets

   :create-coa
   :read-coa
   :delete-coa
   :list-coas


   ;; sightings
   :create-sighting
   :delete-sighting
   :read-sighting
   :list-sightings-by-observable
   :list-sightings-by-indicator
   :list-sightings

   ;; incidents
   :create-incident
   :delete-incident
   :read-incident
   :list-incidents-by-observable
   :list-incidents-by-indicator
   :list-incidents

   ;; relations
   :create-relation
   :delete-relation
   :read-relation
   :list-relations

   ])

(def Capability
  (apply s/enum capabilities))

(def OrgId s/Int)

(def Role s/Str)

(s/defschema AuthRole
  {:org_id OrgId
   :role Role
   :capabilities #{Capability}})
