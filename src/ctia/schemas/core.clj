(ns ctia.schemas.core
  (:require [ctim.schemas
             [verdict :as vs]
             [bundle :as bundle]
             [common :as cos]
             [vocabularies :as vocs]]
            [flanders
             [schema :as f-schema]
             [spec :as f-spec]
             [utils :as fu]]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [Bool Str]]))

(sc/defschema ACLEntity
  (st/optional-keys
   {:authorized_users [Str]
    :authorized_groups [Str]}))

(sc/defschema ACLStoredEntity
  (st/merge ACLEntity
            {(sc/optional-key :groups) [Str]}))

(defmacro defschema [name-sym ddl spec-kw-ns]
  `(do
     (sc/defschema ~name-sym
       (f-schema/->schema ~ddl))
     (f-spec/->spec ~ddl ~spec-kw-ns)))

(defmacro def-stored-schema [name-sym ddl spec-kw-ns]
  `(do
     (sc/defschema ~name-sym
       (st/merge
        (f-schema/->schema ~ddl)
        ACLStoredEntity))
     (f-spec/->spec ~ddl ~spec-kw-ns)))

(defmacro def-acl-schema [name-sym ddl spec-kw-ns]
  `(do
     (sc/defschema ~name-sym
       (st/merge
        (f-schema/->schema ~ddl)
        ACLEntity))
     (f-spec/->spec ~ddl ~spec-kw-ns)))

;; verdict

(def-acl-schema Verdict
  vs/Verdict
  "verdict")

;; Bundle

(def-acl-schema NewBundle
  bundle/NewBundle
  "new-bundle")

(def-acl-schema Bundle
  bundle/Bundle
  "bundle")

;; common

(sc/defschema TLP
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

(def TransientID sc/Str)

(sc/defschema TempIDs
  "Mapping table between transient and permanent IDs"
  {TransientID ID})

(sc/defschema VersionInfo
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
