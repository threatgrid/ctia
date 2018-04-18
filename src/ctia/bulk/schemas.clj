(ns ctia.bulk.schemas
  (:require [ctia.entity.entities :refer [entities]]
            [ctia.schemas.core :refer [Reference]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defschema EntityError
  "Error related to one entity of the bulk"
  {:error s/Any})

(s/defschema Bulk
  (st/optional-keys
   (apply merge {}
          (map
           (fn [[_ {:keys [plural schema]}]]
             {plural schema}) entities))))

(s/defschema StoredBulk
  (st/optional-keys
   (apply merge {}
          (map
           (fn [[_ {:keys [plural stored-schema]}]]
             {plural stored-schema}) entities))))

(s/defschema BulkRefs
  (st/optional-keys
   (apply merge {}
          (map
           (fn [[_ {:keys [plural]}]]
             {plural [(s/maybe Reference)]}) entities))))

(s/defschema NewBulk
  (st/optional-keys
   (apply merge {}
          (map
           (fn [[_ {:keys [plural new-schema]}]]
             {plural new-schema}) entities))))
