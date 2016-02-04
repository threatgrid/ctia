(ns cia.schemas.common
  (:require [cia.schemas.vocabularies :as v]
            [schema.core :as s]))

(def Reference
  "An entity ID, or a URI referring to a remote one."
  s/Str)

(def ID
  "A string uniquely identifying an entity."
  s/Str)

(def URI
  "A URI."
  s/Str)

(def IDRef
  "A URI that points to the JSON representation of the object."
  s/Str)

(def Time
  "Schema definition for all date or timestamp values in GUNDAM."
  org.joda.time.DateTime)

(s/defschema MinimalStixIdentifiers
  {;; :id and :idref must be implemented exclusively
   (s/required-key (s/enum :id :idref)) (s/either ID IDRef)})

(s/defschema GenericStixIdentifiers
  "These fields are common in STIX data models"
  (merge
   MinimalStixIdentifiers
   {:title s/Str
    :description [s/Str]
    (s/optional-key :sort_description) [s/Str]}))

(s/defschema TimeStructure
  "See http://stixproject.github.io/data-model/1.2/cyboxCommon/TimeType/"
  {(s/optional-key :start_time) Time
   (s/optional-key :end_time) Time
   (s/optional-key :produced_time) Time
   (s/optional-key :received_time) Time})

(s/defschema Tool
  "See http://stixproject.github.io/data-model/1.2/cyboxCommon/ToolInformationType/"
  (merge
   GenericStixIdentifiers
   {(s/optional-key :name) s/Str
    (s/optional-key :type) [v/AttackToolType]
    (s/optional-key :references) [s/Str]
    (s/optional-key :vendor) s/Str
    (s/optional-key :version) s/Str
    (s/optional-key :service_pack) s/Str
    ;; Not provided: tool_specific_data
    ;; Not provided: tool_hashes
    ;; Not provided: tool_configuration
    ;; Not provided: execution_environment
    ;; Not provided: errors
    ;; Not provided: metadata
    ;; Not provided: compensation_model
    }))

(s/defschema Source
  "See http://stixproject.github.io/data-model/1.2/stixCommon/InformationSourceType/"
  {(s/optional-key :description) s/Str
   (s/optional-key :idntity) s/Str ;; greatly simplified
   (s/optional-key :role) s/Str ;; empty vocab
   (s/optional-key :contributing_sources) [Reference] ;; more Source's
   (s/optional-key :time) TimeStructure
   (s/optional-key :tools) [Tool]
   ;; Not provided: references
   })

(s/defschema ScopeWrapper
  "For merging into other structures; Commonly repeated structure"
  {(s/optional-key :scope) v/Scope})

(s/defschema Contributor
  "See http://stixproject.github.io/data-model/1.2/cyboxCommon/ContributorType/"
  {(s/optional-key :role) s/Str
   (s/optional-key :name) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :phone) s/Str
   (s/optional-key :organization) s/Str
   (s/optional-key :date) Time
   (s/optional-key :contribution_location) s/Str})

(s/defschema RelatedIdentity
  "See http://stixproject.github.io/data-model/1.2/stixCommon/RelatedIdentityType/"
  {(s/optional-key :confidence) v/HighMedLow
   (s/optional-key :information_source) Source
   (s/optional-key :relationship) s/Str ;; empty vocab
   :identity Reference ;; Points to Identity
   })

(s/defschema Identity
  "See http://stixproject.github.io/data-model/1.2/stixCommon/IdentityType/"
  (merge
   MinimalStixIdentifiers
   {:name s/Str
    :related_identities [RelatedIdentity]}))

(s/defschema Activity
  "See http://stixproject.github.io/data-model/1.2/stixCommon/ActivityType/"
  {:date_time Time
   :description s/Str})
