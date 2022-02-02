(ns ctia.bulk.core
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [ctia.auth :as auth]
   [ctia.domain.entities :as ent :refer [with-long-id short-id->long-id]]
   [ctia.entity.entities :refer [all-entities]]
   [ctia.flows.crud :as flows]
   [ctia.schemas.core :as schemas :refer [APIHandlerServices]]
   [ctia.schemas.utils :as csu]
   [ctia.store :as store]
   [ring.util.http-response :refer [bad-request]]
   [schema-tools.core :as st]
   [schema.core :as s]
   [ctim.domain.id :as id]))

;; TODO def => defn
(def bulk-entity-mapping
  (into {}
        (map (fn [{:keys [entity plural]}]
               {entity (-> plural
                           name
                           (str/replace #"-" "_")
                           keyword)})
             (vals (all-entities)))))

(def inverted-bulk-entity-mapping
  (set/map-invert bulk-entity-mapping))

(defn bulk-key
  "Converts an entity type to a bulk key
   Ex: :attack_pattern -> :attack-patterns"
  [entity-type]
  (get bulk-entity-mapping entity-type))

(defn entity-type-from-bulk-key
  "Converts a bulk entity key to an entity type
   Ex: :attack_patterns -> :attack-pattern"
  [k]
  (get inverted-bulk-entity-mapping k))

(s/defn create-fn
  "return the create function provided an entity type key"
  [k auth-identity params
   {{:keys [get-store]} :StoreService} :- APIHandlerServices]
  #(-> (get-store k)
       (store/create-record
         %
         (auth/ident->map auth-identity)
         params)))

(s/defschema ReadFnServices
  {:StoreService (-> APIHandlerServices
                     (st/get-in [:StoreService])
                     (csu/select-all-keys [:get-store])
                     (st/assoc s/Keyword s/Any))
   s/Keyword s/Any})

(s/defn read-fn
  "return the create function provided an entity type key"
  [k auth-identity params
   {{:keys [get-store]} :StoreService} :- ReadFnServices]
  #(-> (get-store k)
       (store/read-record % (auth/ident->map auth-identity) params))) 

(s/defn create-entities
  "Create many entities provided their type and returns a list of ids"
  [new-entities entity-type tempids auth-identity params
   services :- APIHandlerServices]
  (when (seq new-entities)
    (let [{:keys [realize-fn new-spec]} (get (all-entities) entity-type)]
      (update (flows/create-flow
                :services services
                :entity-type entity-type
                :realize-fn realize-fn
                :store-fn (create-fn entity-type auth-identity params services)
                :long-id-fn #(with-long-id % services)
                :enveloped-result? true
                :identity auth-identity
                :entities new-entities
                :tempids tempids
                :spec new-spec)
              :data
              (partial map (fn [{:keys [error id] :as result}]
                             (if error result id)))))))

(s/defschema ReadEntitiesServices
  {:ConfigService (-> APIHandlerServices
                      (st/get-in [:ConfigService])
                      (csu/select-all-keys [:get-in-config])
                      (st/assoc s/Keyword s/Any))
   :StoreService (-> APIHandlerServices
                     (st/get-in [:StoreService])
                     (csu/select-all-keys [:get-store])
                     (st/assoc s/Keyword s/Any))
   s/Keyword s/Any})

(s/defn read-entities
  "Retrieve many entities of the same type provided their ids and common type"
  [ids entity-type auth-identity
   services :- ReadEntitiesServices]
  (let [read-entity (read-fn entity-type auth-identity {} services)]
    ;; TODO search by ids
    (pmap (fn [id]
           (try
             (some-> (read-entity id)
                     (with-long-id services))
             (catch Exception e
               (log/error (pr-str e)))))
         ids)))

(defn to-long-id
  [id services]
  (cond-> id
    (id/valid-short-id? id) (short-id->long-id services)))

(defn format-bulk-flow-res
  "format bulk res with-long-id for a given entity type result"
  [results services]
  (into {}
        (map (fn [[action res]]
               {action
                (case action
                  :errors (format-bulk-flow-res res services)
                  (map #(to-long-id % services) res))}))
        results))

(s/defn make-bulk-result
  [{:keys [results not-found services] :as _fm} :- flows/FlowMap]
  (let [formatted (format-bulk-flow-res results services)]
    (cond-> formatted
      (seq not-found)
      (update-in [:errors :not-found]
                 concat
                 (map #(to-long-id % services) not-found)))))

(s/defn delete-fn
  "return the delete function provided an entity type key"
  [k auth-identity params
   {{:keys [get-store]} :StoreService} :- APIHandlerServices]
  #(-> (get-store k)
       (store/bulk-delete
         %
         (auth/ident->map auth-identity)
         params)))

(s/defn update-fn
  "return the update function provided an entity type key"
  [k auth-identity params
   {{:keys [get-store]} :StoreService} :- APIHandlerServices]
  #(-> (get-store k)
       (store/bulk-update
        %
        (auth/ident->map auth-identity)
        params)))

(defn get-success-entities-fn
  [action]
  (fn [{:keys [results entities services] :as _fm}]
    (let [successful-long-ids
          (set
           (map #(to-long-id % services)
                (action results)))
          filter-fn (fn [entity]
                      (contains? successful-long-ids
                                 (:id entity)))]
      (filter filter-fn entities))))

(s/defn delete-entities
  "delete many entities provided their type and returns a list of ids"
  [entity-ids entity-type auth-identity params
   services :- APIHandlerServices]
  (when (seq entity-ids)
    (let [get-fn #(read-entities %  entity-type auth-identity services)]
      (flows/delete-flow
       :services services
       :entity-type entity-type
       :entity-ids entity-ids
       :get-fn get-fn
       :delete-fn (delete-fn entity-type auth-identity params services)
       :long-id-fn nil
       :identity auth-identity
       :make-result make-bulk-result
       :get-success-entities (get-success-entities-fn :deleted)))))

(s/defn update-entities
  "update many entities provided their type and returns errored and successed entities' ids"
  [entities entity-type auth-identity params
   services :- APIHandlerServices]
  (when (seq entities)
    (let [get-fn #(read-entities %  entity-type auth-identity services)
          {:keys [realize-fn new-spec]} (get (all-entities) entity-type)]
      (flows/update-flow
       :entities entities
       :services services
       :get-fn get-fn
       :realize-fn realize-fn
       :update-fn (update-fn entity-type auth-identity params services)
       :long-id-fn #(with-long-id % services)
       :entity-type entity-type
       :identity auth-identity
       :spec new-spec
       :make-result make-bulk-result
       :get-success-entities (get-success-entities-fn :updated)))))

(s/defn patch-entities
  "patch many entities provided their type and returns errored and successed entities' ids"
  [patches entity-type auth-identity params
   services :- APIHandlerServices]
  (when (seq patches)
    (let [get-fn #(read-entities %  entity-type auth-identity services)
          {:keys [realize-fn new-spec]} (get (all-entities) entity-type)]
      (flows/patch-flow
       :services services
       :get-fn get-fn
       :realize-fn realize-fn
       :update-fn (update-fn entity-type auth-identity params services)
       :long-id-fn #(with-long-id % services)
       :entity-type entity-type
       :identity auth-identity
       :patch-operation :replace
       :partial-entities patches
       :spec new-spec
       :make-result make-bulk-result
       :get-success-entities (get-success-entities-fn :updated)))))

(defn gen-bulk-from-fn
  "Kind of fmap but adapted for bulk

  ~~~~.clojure
  (gen-bulk-from-fn f {k [v1 ... vn]} args)
  => {k (map #(apply f % (singular k) args) [v1 ... vn])}
  ~~~~
  "
  [func bulk & args]
  (try
    (into {}
          (comp
           (remove (comp empty? second))
           (map (fn [[bulk-k entities]]
                  (let [entity-type (entity-type-from-bulk-key bulk-k)]
                    [bulk-k
                     (apply func
                            entities
                            entity-type args)]))))
          bulk)
    (catch java.util.concurrent.ExecutionException e
      (throw (.getCause e)))))

(defn merge-tempids
  "Merges tempids from all entities
   {:entity-type1 {:data []
                   :tempids {transientid1 id1
                             transientid2 id2}}
    :entity-type2 {:data []
                   :tempids {transientid3 id3
                             transientid4 id4}}}

   ->

   {transientid1 id1
    transientid2 id2
    transientid3 id3
    transientid4 id4}

  The create-entities set the enveloped-result? to True in the flow
  configuration to get :data and :tempids for each entity in the result."
  [entities-by-type]
  (->> entities-by-type
       (map (fn [[_ v]] (:tempids v)))
       (reduce into {})))

(defn bulk-refresh? [get-in-config]
  (get-in-config
    [:ctia :store :bulk-refresh]
    "false"))

(defn bulk-size [bulk]
  (reduce + (map count (vals bulk))))

(defn get-bulk-max-size [get-in-config]
  (get-in-config [:ctia :http :bulk :max-size]))

(defn bad-request?
  [bulk
   {{:keys [get-in-config]} :ConfigService}]
  (when (> (bulk-size bulk) (get-bulk-max-size get-in-config))
    (bad-request (str "Bulk max number of entities: "
                      (get-bulk-max-size get-in-config)))))

(s/defn create-bulk
  "Creates entities in bulk. To define relationships between entities,
   transient IDs can be used. They are automatically converted into
   real IDs.

   1. Creates all entities except Relationships
   2. Creates Relationships with mapping between transient and real IDs"
  ([bulk login services :- APIHandlerServices] (create-bulk bulk {} login {} services))
  ([bulk tempids login params
    {{:keys [get-in-config]} :ConfigService :as services} :- APIHandlerServices]
   (let [{:keys [refresh]
          :or   {refresh (bulk-refresh? get-in-config)}} params
         new-entities (gen-bulk-from-fn
                       create-entities
                       (dissoc
                        bulk
                        :relationships
                        ;; AssetMapping and AssetProperties have asset-ref field
                        ;; that needs to be resolved, so we delay the creation
                        ;; of these entities to be after we create Assets
                        :asset_mappings :asset_properties)
                       tempids
                       login
                       {:refresh refresh}
                       services)
         entities-tempids (into tempids (merge-tempids new-entities))
         new-linked-ents (gen-bulk-from-fn
                          create-entities
                          (select-keys
                           bulk
                           [:relationships
                            :asset_mappings
                            :asset_properties])
                          entities-tempids
                          login
                          {:refresh refresh}
                          services)
         all-tempids (merge entities-tempids (merge-tempids new-linked-ents))
         all-entities (merge new-entities new-linked-ents)
         ;; Extracting data from the enveloped flow result
         ;; {:entity-type {:data [] :tempids {}}
         bulk-refs (->> all-entities
                        (map (fn [[k {:keys [data]}]]
                               {k data}))
                        (into {}))]
     (cond-> bulk-refs
       (seq all-tempids) (assoc :tempids all-tempids)))))

(s/defn fetch-bulk
  [bulk auth-identity
   services :- APIHandlerServices]
  (ent/un-store-map
   (gen-bulk-from-fn read-entities bulk auth-identity services)))

(s/defn delete-bulk
  [bulk auth-identity params
   services :- APIHandlerServices]
  (gen-bulk-from-fn delete-entities bulk auth-identity params services))

(s/defn update-bulk
  [bulk auth-identity params
   services :- APIHandlerServices]
  (gen-bulk-from-fn update-entities bulk auth-identity params services))

(s/defn patch-bulk
  [bulk auth-identity params
   services :- APIHandlerServices]
  (gen-bulk-from-fn patch-entities bulk auth-identity params services))
