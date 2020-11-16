(ns ctia.bulk.schemas
  (:require [clojure.string :as str]
            [ctia.entity.entities :as entities]
            [ctia.schemas.core :refer [TempIDs Reference]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(defn entities-bulk-schema
  [entities sch]
  (st/optional-keys
   (->> entities
        (remove #(:no-bulk? (val %)))
        (map
         (fn [[_ {:keys [plural]
                  :as entity}]]
           (let [bulk-schema
                 (if (keyword? sch)
                   [(s/maybe
                     (get entity sch))] sch)]
             {(-> plural
                  name
                  (str/replace #"-" "_")
                  keyword)
              bulk-schema})))
        (apply merge {}))))

(s/defschema EntityError
  "Error related to one entity of the bulk"
  {:error s/Any})

; TODO def => defn
(s/defschema Bulk
  (entities-bulk-schema (entities/all-entities) :schema))

; TODO def => defn
(s/defschema StoredBulk
  (entities-bulk-schema (entities/all-entities) :stored-schema))

; TODO def => defn
(s/defschema BulkRefs
  (st/assoc
   (entities-bulk-schema (entities/all-entities) [(s/maybe Reference)])
   (s/optional-key :tempids) TempIDs))

; TODO def => defn
(s/defschema NewBulk
  (entities-bulk-schema (entities/all-entities) :new-schema))
