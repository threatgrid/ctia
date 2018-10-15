(ns ctia.bulk.schemas
  (:require [ctia.entity.entities :refer [entities]]
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
                  (clojure.string/replace #"-" "_")
                  keyword)
              bulk-schema})))
        (apply merge {}))))

(s/defschema EntityError
  "Error related to one entity of the bulk"
  {:error s/Any})

(s/defschema Bulk
  (entities-bulk-schema entities :schema))

(s/defschema StoredBulk
  (entities-bulk-schema entities :stored-schema))

(s/defschema BulkRefs
  (st/assoc
   (entities-bulk-schema entities [(s/maybe Reference)])
   (s/optional-key :tempids) TempIDs))

(s/defschema NewBulk
  (entities-bulk-schema entities :new-schema))
