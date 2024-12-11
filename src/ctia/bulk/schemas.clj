(ns ctia.bulk.schemas
  (:require [clojure.string :as str]
            [ctia.entity.entities :as entities]
            [ctia.schemas.core :as schemas :refer [TempIDs Reference GetEntitiesServices]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defschema EntitiesResult
  [(s/conditional
     string? schemas/ID
     :else {(s/optional-key :error) (s/conditional
                                      string? s/Str
                                      :else {:type (s/conditional
                                                     string? s/Str
                                                     :else s/Keyword)
                                             :reason s/Str
                                             (s/optional-key :index) s/Str
                                             (s/optional-key :index_uuid) s/Str})
            (s/optional-key :msg) s/Str
            (s/optional-key :entity) (s/pred map?)
            (s/optional-key :type) (s/conditional
                                     string? s/Str
                                     :else s/Keyword)
            (s/optional-key :id) (s/maybe s/Str)
            s/Keyword s/Any})])

(defn entity-schema
  ([entity sch services]
   (entity-schema entity sch services {}))
  ([{:keys [plural] :as entity} sch services {:keys [required-ids?]}]
   (let [bulk-schema
         (if (keyword? sch)
           [(s/maybe
             (let [ent-schema (get entity sch)]
               (cond-> (if (fn? ent-schema)
                         (ent-schema services) ent-schema)
                 required-ids? (st/required-keys [:id]))))]
           (if required-ids?
             (st/required-keys sch :id)
             sch))]
     {(-> plural
          name
          (str/replace #"-" "_")
          keyword)
      bulk-schema})))

(defn entities-bulk-schema
  ([entities sch services]
   (entities-bulk-schema entities sch services {}))
  ([entities sch services opts]
   (st/optional-keys
    (->> (vals entities)
         (remove :no-bulk?)
         (map #(entity-schema % sch services opts))
         (apply merge {})))))

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
  (entities-bulk-schema (get-entities services) :schema services))

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
    (entities-bulk-schema patchable-entities
                          :partial-schema
                          services
                          {:required-ids? true})))

(s/defn BulkRefs :- (s/protocol s/Schema)
  [services :- GetEntitiesServices]
  (entities-bulk-schema (get-entities services) EntitiesResult services))

(s/defn BulkCreateRes :- (s/protocol s/Schema)
  [services :- GetEntitiesServices]
  (st/assoc (BulkRefs services)
            (s/optional-key :tempids)
            TempIDs))

(s/defn NewBulk :- (s/protocol s/Schema)
  "Returns NewBulk schema without disabled entities"
  [services :- GetEntitiesServices]
  (entities-bulk-schema (get-entities services) :new-schema services))

(s/defn BulkUpdate :- (s/protocol s/Schema)
  "Returns NewBulk schema without disabled entities and required ids"
  [services :- GetEntitiesServices]
  (entities-bulk-schema (get-entities services)
                        :new-schema
                        services
                        {:required-ids? true}))

(def NewBulkDelete BulkRefs)

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
  (entities-bulk-schema (get-entities services) BulkActions services))
