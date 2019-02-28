(ns ctia.bundle.schemas
  (:require [schema.core :as s]
            [ctia.schemas.core :as csc]
            [schema-tools.core :as st]))

(s/defschema EntityImportResult
  (st/optional-keys
   {:id s/Str
    :original_id s/Str
    :result (s/enum "error" "created" "exists")
    :type s/Keyword
    :external_id s/Str
    :error s/Any}))

(s/defschema EntityImportData
  "Data structure used to keep a link between
   the transient ID, the external_id and the ID of an entity
   during the import process."
  (st/merge
   EntityImportResult
   (st/optional-keys
    {:new-entity s/Any
     :old-entity s/Any})))

(s/defschema BundleImportData {s/Keyword [EntityImportData]})

(s/defschema BundleImportResult
  {:results [EntityImportResult]})

(s/defschema NewBundleExport
  (st/open-schema csc/NewBundle))
