(ns ctia.bulk.core
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [ctia
             [auth :as auth]
             [properties :as p]
             [store :as store :refer [read-store write-store]]]
            [ctia.domain.entities :as ent :refer [with-long-id]]
            [ctia.entity.entities :refer [entities]]
            [ctia.flows.crud :as flows]
            [ring.util.http-response :refer :all]))

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

(defn create-fn
  "return the create function provided an entity type key"
  [k auth-identity params]
  #(write-store
    k store/create-record
    % (auth/ident->map auth-identity) params))

(defn read-fn
  "return the create function provided an entity type key"
  [k auth-identity params]
  #(read-store
    k store/read-record
    % (auth/ident->map auth-identity) params))

(defn create-entities
  "Create many entities provided their type and returns a list of ids"
  [new-entities entity-type tempids auth-identity params]
  (when (seq new-entities)
    (update (flows/create-flow
             :entity-type entity-type
             :realize-fn (-> entities entity-type :realize-fn)
             :store-fn (create-fn entity-type auth-identity params)
             :long-id-fn with-long-id
             :enveloped-result? true
             :identity auth-identity
             :entities new-entities
             :tempids tempids
             :spec (-> entities entity-type :new-spec))
            :data (partial map (fn [{:keys [error id] :as result}]
                                 (if error result id))))))

(defn read-entities
  "Retrieve many entities of the same type provided their ids and common type"
  [ids entity-type auth-identity]
  (let [read-entity (read-fn entity-type auth-identity {})]
    (map (fn [id]
           (try
             (if-let [entity (read-entity id)]
               (with-long-id entity)
               nil)
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

(defn bulk-refresh? []
  (p/get-in-global-properties
    [:ctia :store :bulk-refresh]
    "false"))

(defn create-bulk
  "Creates entities in bulk. To define relationships between entities,
   transient IDs can be used. They are automatically converted into
   real IDs.

   1. Creates all entities except Relationships
   2. Creates Relationships with mapping between transient and real IDs"
  ([bulk login] (create-bulk bulk {} login {}))
  ([bulk tempids login {:keys [refresh] :as params
                        :or {refresh (bulk-refresh?)}}]
   (let [new-entities (gen-bulk-from-fn
                       create-entities
                       (dissoc bulk :relationships)
                       tempids
                       login
                       {:refresh refresh})
         entities-tempids (into tempids
                                (merge-tempids new-entities))
         new-relationships (gen-bulk-from-fn
                            create-entities
                            (select-keys bulk [:relationships])
                            entities-tempids
                            login
                            {:refresh refresh})
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

(defn get-bulk-max-size []
  (p/get-in-global-properties [:ctia :http :bulk :max-size]))

(defn fetch-bulk
  [entities-map auth-identity]
  (let [bulk (into {} (remove (comp empty? second) entities-map))]
    (if (> (bulk-size bulk) (get-bulk-max-size))
      (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size)))
      (ent/un-store-map
       (gen-bulk-from-fn read-entities bulk auth-identity)))))
