(ns ctia.bulk.schemas
  (:require [clojure.string :as str]
            [ctia.entity.entities :as entities]
            [ctia.schemas.core :refer [TempIDs Reference GetEntitiesServices]]
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

(s/defn get-entities :- [s/Any]
  "Returns list of enabled entities"
  [{{:keys [enabled?]} :FeaturesService} :- GetEntitiesServices]
  (->> (entities/all-entities) (filter (fn [[k _]] (enabled? k)))))

(s/defn Bulk :- (s/protocol s/Schema)
  "Returns Bulk schema without disabled entities"
  [services :- GetEntitiesServices]
  (entities-bulk-schema (get-entities services) :schema))

(s/defn BulkRefs :- (s/protocol s/Schema)
  [services :- GetEntitiesServices]
  (st/assoc
   (entities-bulk-schema (get-entities services) [(s/maybe Reference)])
   (s/optional-key :tempids) TempIDs))

(s/defn NewBulk :- (s/protocol s/Schema)
  "Returns NewBulk schema without disabled entities"
  [services :- GetEntitiesServices]
  (entities-bulk-schema (get-entities services) :new-schema))
