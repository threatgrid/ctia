(ns ctia.schemas.core
  (:require [ctia.schemas.utils :as csutils]
            [ctim.schemas
             [bundle :as bundle]
             [common :as cos]
             [verdict :as vs]
             [vocabularies :as vocs]]
            [flanders
             [schema :as f-schema]
             [spec :as f-spec]]
            [schema-tools.core :as st]
            [schema.core :as s :refer [Bool Str]]))

(def base-stored-entity-entries
  {:id s/Str
   :owner s/Str
   :groups [s/Str]
   :created java.util.Date
   (s/optional-key :modified) java.util.Date})

(s/defschema Entity
  (st/merge
   {:entity s/Keyword
    :plural s/Keyword
    :new-spec (s/either s/Keyword s/Any)
    :schema (s/protocol s/Schema)
    :partial-schema (s/protocol s/Schema)
    :partial-list-schema (s/protocol s/Schema)
    :stored-schema (s/protocol s/Schema)
    :partial-stored-schema (s/protocol s/Schema)
    :es-store s/Any
    :es-mapping {s/Any s/Any}}
   (st/optional-keys
    {:new-schema (s/protocol s/Schema)
     :route-context s/Str
     :routes s/Any
     :tags [s/Str]
     :capabilities #{s/Keyword}
     :no-bulk? s/Bool
     :no-api? s/Bool
     :realize-fn s/Any})))

(s/defschema OpenCTIMSchemaVersion
  {(s/optional-key :schema_version) s/Str})

(s/defschema CTIAEntity
  (st/merge
   OpenCTIMSchemaVersion
   (st/optional-keys
    {:authorized_users [Str]
     :authorized_groups [Str]})))

(s/defschema CTIAStoredEntity
  (st/merge CTIAEntity
            base-stored-entity-entries))

(defmacro defschema [name-sym ddl spec-kw-ns]
  `(do
     (s/defschema ~name-sym
       (f-schema/->schema ~ddl))
     (f-spec/->spec ~ddl ~spec-kw-ns)))

(defmacro def-stored-schema
  [name-sym sch]
  `(do
     (s/defschema ~name-sym
       (csutils/recursive-open-schema-version
        (st/merge
         ~sch
         CTIAStoredEntity)))))

(defmacro def-acl-schema [name-sym ddl spec-kw-ns]
  `(do
     (s/defschema ~name-sym
       (csutils/recursive-open-schema-version
        (st/merge
         (f-schema/->schema ~ddl)
         CTIAEntity)))
     (f-spec/->spec ~ddl ~spec-kw-ns)))

;; verdict

(def-acl-schema Verdict
  vs/Verdict
  "verdict")

;; Bundle

(def-acl-schema CTIMNewBundle
  bundle/NewBundle
  "new-bundle")

(def-acl-schema CTIMBundle
  bundle/Bundle
  "bundle")


;; Casebooks should be considered fully separate from CTIM
(s/defschema NewBundle
  (st/dissoc
   (csutils/recursive-open-schema-version CTIMNewBundle) :casebooks))

(s/defschema Bundle
  (st/dissoc
   (csutils/recursive-open-schema-version CTIMBundle) :casebooks))

;; common

(s/defschema TLP
  (f-schema/->schema
   cos/TLP))

(defschema Observable
  cos/Observable
  "common.observable")

(defschema Reference
  cos/Reference
  "common.reference")

(defschema ID
  cos/ID
  "common.id")

(def TransientID s/Str)

(s/defschema TempIDs
  "Mapping table between transient and permanent IDs"
  {TransientID ID})

(s/defschema StatusInfo
  "Status information for a specific instance of CTIA"
  {:status s/Str})

(s/defschema VersionInfo
  "Version information for a specific instance of CTIA"
  {:base Str
   :ctim-version Str
   :ctia-build Str
   :beta Bool
   :ctia-config Str
   :ctia-supported_features [Str]})

;; vocabularies

(defschema ObservableTypeIdentifier
  vocs/ObservableTypeIdentifier
  "vocab.observable-type-id")
