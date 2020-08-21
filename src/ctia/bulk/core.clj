(ns ctia.bulk.core
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [ctia
             [auth :as auth]
             [properties :as p]
             [store :as store]]
            [ctia.domain.entities :as ent :refer [with-long-id]]
            [ctia.entity.entities :refer [entities]]
            [ctia.flows.crud :as flows]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [ring.util.http-response :refer [bad-request]]
            [schema.core :as s]))

(def bulk-entity-mapping
  (into {}
        (map (fn [{:keys [entity plural]}]
               {entity (-> plural
                           name
                           (str/replace #"-" "_")
                           keyword)})
             (vals entities))))

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
  [k auth-identity params {{:keys [write-store]} :StoreService :as _services_} :- APIHandlerServices]
  #(write-store
    k store/create-record
    % (auth/ident->map auth-identity) params))

(s/defn read-fn
  "return the create function provided an entity type key"
  [k auth-identity params {{:keys [read-store]} :StoreService :as _services_} :- APIHandlerServices]
  #(read-store
    k store/read-record
    % (auth/ident->map auth-identity) params))

(s/defn create-entities
  "Create many entities provided their type and returns a list of ids"
  [new-entities entity-type tempids auth-identity params
   {{:keys [get-in-config]} :ConfigService
    :as services} :- APIHandlerServices]
  (when (seq new-entities)
    (update (flows/create-flow
             :services services
             :entity-type entity-type
             :realize-fn (-> entities entity-type :realize-fn)
             :store-fn (create-fn entity-type auth-identity params services)
             :long-id-fn #(with-long-id % get-in-config)
             :enveloped-result? true
             :identity auth-identity
             :entities new-entities
             :tempids tempids
             :spec (-> entities entity-type :new-spec))
            :data (partial map (fn [{:keys [error id] :as result}]
                                 (if error result id))))))

(s/defn read-entities
  "Retrieve many entities of the same type provided their ids and common type"
  [ids entity-type auth-identity
   {{:keys [get-in-config]} :ConfigService
    :as services} :- APIHandlerServices]
  (let [read-entity (read-fn entity-type auth-identity {} services)]
    (map (fn [id]
           (try
             (some-> (read-entity id)
                     (with-long-id get-in-config))
             (catch Exception e
               (do (log/error (pr-str e))
                   nil)))) ids)))

(defn gen-bulk-from-fn
  "Kind of fmap but adapted for bulk

  ~~~~.clojure
  (gen-bulk-from-fn f {k [v1 ... vn]} args)
  ===> {k (map #(apply f % (singular k) args) [v1 ... vn])}
  ~~~~
  "
  [func bulk & args]
  (try
    (->> bulk
         (pmap (fn [[bulk-k entities]]
                 (let [entity-type (entity-type-from-bulk-key bulk-k)]
                   [bulk-k
                    (apply func
                           entities
                           entity-type args)])))
         (into {}))
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

(s/defn create-bulk
  "Creates entities in bulk. To define relationships between entities,
   transient IDs can be used. They are automatically converted into
   real IDs.

   1. Creates all entities except Relationships
   2. Creates Relationships with mapping between transient and real IDs"
  ([bulk login services :- APIHandlerServices] (create-bulk bulk {} login {} services))
  ([bulk tempids login params {{:keys [get-in-config]} :ConfigService :as services} :- APIHandlerServices]
   (let [{:keys [refresh] :as params
          :or {refresh (bulk-refresh? get-in-config)}} params
         new-entities (gen-bulk-from-fn
                       create-entities
                       (dissoc bulk :relationships)
                       tempids
                       login
                       {:refresh refresh}
                       services)
         entities-tempids (into tempids
                                (merge-tempids new-entities))
         new-relationships (gen-bulk-from-fn
                            create-entities
                            (select-keys bulk [:relationships])
                            entities-tempids
                            login
                            {:refresh refresh}
                            services)
         all-tempids (merge entities-tempids
                            (merge-tempids new-relationships))
         all-entities (into new-entities new-relationships)
         ;; Extracting data from the enveloped flow result
         ;; {:entity-type {:data [] :tempids {}}
         bulk-refs (->> all-entities
                        (map (fn [[k {:keys [data]}]]
                               {k data}))
                        (into {}))]
     (cond-> bulk-refs
       (seq all-tempids) (assoc :tempids all-tempids)))))

(defn bulk-size [bulk]
  (apply + (map count (vals bulk))))

(defn get-bulk-max-size [get-in-config]
  (get-in-config [:ctia :http :bulk :max-size]))

(s/defn fetch-bulk
  [entities-map auth-identity {{:keys [get-in-config]} :ConfigService :as services} :- APIHandlerServices]
  (let [bulk (into {} (remove (comp empty? second) entities-map))]
    (if (> (bulk-size bulk) (get-bulk-max-size get-in-config))
      (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size get-in-config)))
      (ent/un-store-map
       (gen-bulk-from-fn read-entities bulk auth-identity services)))))
