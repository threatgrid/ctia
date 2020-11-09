(ns ctia.bundle.schemas
  (:require [schema.core :as s]
            [schema-tools.core :as st]
            [ctia.schemas.core :as csc]
            [ctia.entity.entities :refer [entities]]))

(s/defschema EntityImportResult
  (st/optional-keys
   {:id s/Str
    :original_id s/Str
    :result (s/enum "error" "created" "exists")
    :type s/Keyword
    :external_ids [s/Str]
    :error s/Any
    :msg s/Str}))

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

(s/defschema EntityTypes
  (apply s/enum
         (-> (keys entities)
             set
             (disj :relationship :event :identity))))

(s/defschema BundleExportOptions
  (st/optional-keys
   {:related_to [(s/enum :source_ref :target_ref)]
    :source_type EntityTypes
    :target_type EntityTypes
    :include_related_entities s/Bool}))

(s/defschema BundleExportIds
  {:ids [s/Str]})

(s/defschema BundleExportQuery
  (merge BundleExportIds
         BundleExportOptions))

(s/defschema FindByExternalIdsServices
  {:StoreService {:read-store (s/pred ifn?)
                  s/Keyword s/Any}
   s/Keyword s/Any})
