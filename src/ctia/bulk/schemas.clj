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
                     (get entity sch))]
                   sch)]
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
  [{{:keys [entity-enabled?]} :FeaturesService} :- GetEntitiesServices]
  (->> (entities/all-entities) (filter (fn [[k _]] (entity-enabled? k)))))

(s/defn Bulk :- (s/protocol s/Schema)
  "Returns Bulk schema without disabled entities"
  [services :- GetEntitiesServices]
  (entities-bulk-schema (get-entities services) :schema))

(defn bulk-patch-capabilities
  [services]
  (->> (get-entities services)
       (map second)
       (filter :can-patch?)
       (map :patch-capabilities)
       set))

(s/defn BulkPatch :- (s/protocol s/Schema)
  "Returns BulkPatch schema without disabled entities and only entities that can be patched"
  [services :- GetEntitiesServices]
  (let [patchable-entities (into {}
                                 (filter #(:can-patch? (second %)))
                                 (get-entities services))]
    (entities-bulk-schema patchable-entities :partial-schema)))

(s/defn BulkUpdate :- (s/protocol s/Schema)
  "Returns BulkUpdate schema without disabled entities"
  [services :- GetEntitiesServices]
  (entities-bulk-schema (get-entities services) :new-schema))

(s/defn BulkRefs :- (s/protocol s/Schema)
  [services :- GetEntitiesServices]
  (entities-bulk-schema (get-entities services) [(s/maybe Reference)]))

(s/defn BulkCreateRes :- (s/protocol s/Schema)
  [services :- GetEntitiesServices]
  (st/assoc (BulkRefs services)
            (s/optional-key :tempids)
            TempIDs))

(s/defn NewBulk :- (s/protocol s/Schema)
  "Returns NewBulk schema without disabled entities"
  [services :- GetEntitiesServices]
  (entities-bulk-schema (get-entities services) :new-schema))

(s/defn NewBulkDelete
  "Returns NewBulk schema without disabled entities"
  [services :- GetEntitiesServices]
  (entities-bulk-schema (get-entities services) [(s/maybe Reference)]))

(s/defschema BulkErrors
  (st/optional-keys
   {:not-found [Reference]
    :forbidden [Reference]
    :internal-error [Reference]}))

(s/defschema BulkActions
  (st/optional-keys
   {:deleted [Reference]
    :updated [Reference]
    :errors BulkErrors}))

(s/defn BulkActionsRefs :- (s/protocol s/Schema)
  [services :- GetEntitiesServices]
  (entities-bulk-schema (get-entities services) BulkActions))
