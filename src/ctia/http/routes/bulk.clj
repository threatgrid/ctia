(ns ctia.http.routes.bulk
  (:require [compojure.api.sweet :refer :all]
            [clojure.tools.logging :as log]
            [ctia.auth :as auth]
            [ctia.domain.entities :as ent
             :refer [with-long-id-fn realize-fn]]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common :as common]
            [ctia.lib.keyword :refer [singular]]
            [ctia.schemas.bulk :refer [Bulk BulkRefs EntityError NewBulk]]
            [ctia.properties :refer [properties]]
            [ctia.store :as store
             :refer [write-store read-store]]
            [ctia.schemas.core :refer [Reference]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [clojure.set :as set]))

(defn create-fn
  "return the create function provided an entity type key"
  [k auth-identity params]
  #(write-store
    k (get store/create-fn k)
    % (auth/ident->map auth-identity) params))

(defn read-fn
  "return the create function provided an entity type key"
  [k auth-identity params]
  #(read-store
    k (get store/read-fn k)
    % (auth/ident->map auth-identity) params))

(defn create-entities
  "Create many entities provided their type and returns a list of ids"
  [new-entities entity-type tempids auth-identity params]
  (when (seq new-entities)
    (let [with-long-id (with-long-id-fn entity-type)]
      (update (flows/create-flow
               :entity-type entity-type
               :realize-fn (realize-fn entity-type)
               :store-fn (create-fn entity-type auth-identity params)
               :long-id-fn with-long-id
               :enveloped-result? true
               :identity auth-identity
               :entities new-entities
               :tempids tempids)
              :data (partial map (fn [{:keys [error id] :as result}]
                                   (if error result id)))))))

(defn read-entities
  "Retrieve many entities of the same type provided their ids and common type"
  [ids entity-type auth-identity]
  (let [read-entity (read-fn entity-type auth-identity {})
        with-long-id (with-long-id-fn entity-type)]
    (map (fn [id] (try (with-long-id
                         (read-entity id))
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
  (->> bulk
       (pmap (fn [[entity-type entities]]
               [entity-type
                (apply func entities (singular entity-type) args)]))
       (into {})))

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

(defn bulk-key
  "Returns the bulk key for a given entity type"
  [entity-type]
  (-> (name entity-type)
      (str "s")
      keyword))

(defn create-bulk
  "Creates entities in bulk. To define relationships between entities,
   transient IDs can be used. They are automatically converted into
   real IDs.

   1. Creates all entities except Relationships
   2. Creates Relationships with mapping between transient and real IDs"
  ([bulk login] (create-bulk bulk {} login {}))
  ([bulk tempids login {:keys [refresh] :as params
                        :or {refresh "false"}}]
   (let [new-entities (gen-bulk-from-fn
                       create-entities
                       (dissoc bulk :relationships)
                       tempids
                       login
                       {:refresh refresh})
         entities-tempids (merge-tempids new-entities)
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
  (get-in @properties [:ctia :http :bulk :max-size]))

(defroutes bulk-routes
  (context "/bulk" []
           :tags ["Bulk"]
           (POST "/" []
                 :return BulkRefs
                 :body [bulk NewBulk {:description "a new Bulk object"}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "POST many new entities using a single HTTP call"
                 :identity login
                 :capabilities #{:create-actor
                                 :create-attack-pattern
                                 :create-campaign
                                 :create-coa
                                 :create-data-table
                                 :create-exploit-target
                                 :create-feedback
                                 :create-incident
                                 :create-investigation
                                 :create-indicator
                                 :create-judgement
                                 :create-malware
                                 :create-relationship
                                 :create-scratchpad
                                 :create-sighting
                                 :create-tool}
                 (if (> (bulk-size bulk)
                        (get-bulk-max-size))
                   (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size)))
                   (common/created (create-bulk bulk login))))

           (GET "/" []
                :return (s/maybe Bulk)
                :summary "GET many entities at once"
                :query-params [{actors          :- [Reference] []}
                               {attack-patterns :- [Reference] []}
                               {campaigns       :- [Reference] []}
                               {coas            :- [Reference] []}
                               {data-tables     :- [Reference] []}
                               {exploit-targets :- [Reference] []}
                               {feedbacks       :- [Reference] []}
                               {incidents       :- [Reference] []}
                               {indicators      :- [Reference] []}
                               {investigations  :- [Reference] []}
                               {judgements      :- [Reference] []}
                               {malwares        :- [Reference] []}
                               {relationships   :- [Reference] []}
                               {scratchpads     :- [Reference] []}
                               {sightings       :- [Reference] []}
                               {tools           :- [Reference] []}]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities #{:read-actor
                                :read-attack-pattern
                                :read-campaign
                                :read-coa
                                :read-data-table
                                :read-exploit-target
                                :read-feedback
                                :read-incident
                                :read-indicator
                                :read-investigation
                                :read-judgement
                                :read-malware
                                :read-relationship
                                :read-scratchpad
                                :read-sighting
                                :read-tool}
                :identity auth-identity
                (let [bulk (into {} (remove (comp empty? second)
                                            {:actors          actors
                                             :attack-patterns attack-patterns
                                             :campaigns       campaigns
                                             :coas            coas
                                             :data-tables     data-tables
                                             :exploit-targets exploit-targets
                                             :feedbacks       feedbacks
                                             :incidents       incidents
                                             :investigations  investigations
                                             :indicators      indicators
                                             :judgements      judgements
                                             :malwares        malwares
                                             :relationships   relationships
                                             :scratchpads     scratchpads
                                             :sightings       sightings
                                             :tools           tools}))]
                  (if (> (bulk-size bulk) (get-bulk-max-size))
                    (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size)))
                    (-> (gen-bulk-from-fn read-entities bulk auth-identity)
                        ent/un-store-map
                        ok))))))
