(ns ctia.http.routes.bulk
  (:require [compojure.api.sweet :refer :all]
            [clojure.tools.logging :as log]
            [ctia.domain.entities :as ent]
            [ctia.domain.entities
             [actor :as act-ent]
             [attack-pattern :as attack-ent]
             [campaign :as cam-ent]
             [coa :as coa-ent]
             [exploit-target :as ept-ent]
             [data-table :as dt-ent]
             [feedback :as fbk-ent]
             [incident :as inc-ent]
             [indicator :as ind-ent]
             [judgement :as jud-ent]
             [malware :as malware-ent]
             [relationship :as rel-ent]
             [sighting :as sig-ent]
             [tool :as tool-ent]]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common :as common]
            [ctia.lib.keyword :refer [singular]]
            [ctia.schemas.bulk :refer [Bulk BulkRefs NewBulk]]
            [ctia.properties :refer [properties]]
            [ctia.store :refer :all]
            [ctia.schemas.core :refer [Reference]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defn realize-fn
  "return the realize function provided an entity type key"
  [k]
  (case k
    :actor          ent/realize-actor
    :attack-pattern ent/realize-attack-pattern
    :campaign       ent/realize-campaign
    :coa            ent/realize-coa
    :data-table     ent/realize-data-table
    :exploit-target ent/realize-exploit-target
    :feedback       ent/realize-feedback
    :incident       ent/realize-incident
    :indicator      ent/realize-indicator
    :judgement      ent/realize-judgement
    :malware        ent/realize-malware
    :relationship   ent/realize-relationship
    :sighting       ent/realize-sighting
    :tool           ent/realize-tool))

(defn create-fn
  "return the create function provided an entity type key"
  [k ident]
  #(write-store
    k (case k
        :actor          create-actors
        :attack-pattern create-attack-patterns
        :campaign       create-campaigns
        :coa            create-coas
        :data-table     create-data-tables
        :exploit-target create-exploit-targets
        :feedback       create-feedbacks
        :incident       create-incidents
        :indicator      create-indicators
        :judgement      create-judgements
        :malware        create-malwares
        :relationship   create-relationships
        :sighting       create-sightings
        :tool           create-tools)
    % ident))

(defn read-fn
  "return the create function provided an entity type key"
  [k ident params]
  #(read-store
    k (case k
        :actor          read-actor
        :attack-pattern read-attack-pattern
        :campaign       read-campaign
        :coa            read-coa
        :data-table     read-data-table
        :exploit-target read-exploit-target
        :feedback       read-feedback
        :incident       read-incident
        :indicator      read-indicator
        :judgement      read-judgement
        :malware        read-malware
        :relationship   read-relationship
        :sighting       read-sighting
        :tool           read-tool)
    % ident params))

(defn with-long-id-fn
  "return the with-long-id function provided an entity type key"
  [k]
  (case k
    :actor          act-ent/with-long-id
    :attack-pattern attack-ent/with-long-id
    :campaign       cam-ent/with-long-id
    :coa            coa-ent/with-long-id
    :data-table     dt-ent/with-long-id
    :exploit-target ept-ent/with-long-id
    :feedback       fbk-ent/with-long-id
    :incident       inc-ent/with-long-id
    :indicator      ind-ent/with-long-id
    :judgement      jud-ent/with-long-id
    :malware        malware-ent/with-long-id
    :relationship   rel-ent/with-long-id
    :sighting       sig-ent/with-long-id
    :tool           tool-ent/with-long-id))

(defn create-entities
  "Create many entities provided their type and returns a list of ids"
  [entities entity-type tempids login]
  (let [with-long-id (with-long-id-fn entity-type)]
    (update (flows/create-flow
             :entity-type entity-type
             :realize-fn (realize-fn entity-type)
             :store-fn (create-fn entity-type login)
             :long-id-fn with-long-id
             :enveloped-result? true
             :identity login
             :entities entities
             :tempids tempids)
            :data
            #(map :id %))))

(defn read-entities
  "Retrieve many entities of the same type provided their ids and common type"
  [ids entity-type ident]
  (let [read-entity (read-fn entity-type ident {})
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
  (reduce (fn [acc entity-type]
            (assoc acc
                   entity-type
                   (apply func
                          (get bulk entity-type)
                          (singular entity-type)
                          args)))
          {}
          (keys bulk)))

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
  [entities]
  (->> entities
       (map (fn [[_ v]] (:tempids v)))
       (reduce into {})))

(defn create-bulk
  "Creates entities in bulk. To define relationships between entities,
   transient IDs can be used. They are automatically converted into
   real IDs.

   1. Creates all entities except Relationships
   2. Creates Relationships with mapping between transient and real IDs"
  ([bulk login] (create-bulk bulk {} login))
  ([bulk tempids login]
   (let [new-entities (gen-bulk-from-fn
                       create-entities
                       (dissoc bulk :relationships)
                       tempids
                       login)
         entities-tempids (merge-tempids new-entities)
         new-relationships (gen-bulk-from-fn
                            create-entities
                            (select-keys bulk [:relationships])
                            entities-tempids
                            login)
         all-tempids (merge entities-tempids
                            (merge-tempids new-relationships))
         ;; Extracting data from the enveloped flow result
         ;; {:entity-type {:data [] :tempids {} :errors {}}}
         bulk-refs (->> (into new-entities new-relationships)
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
                                 :create-indicator
                                 :create-judgement
                                 :create-malware
                                 :create-relationship
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
                               {judgements      :- [Reference] []}
                               {malwares        :- [Reference] []}
                               {relationships   :- [Reference] []}
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
                                :read-judgement
                                :read-malware
                                :read-relationship
                                :read-sighting
                                :read-tool}
                :identity identity
                (let [bulk (into {} (remove (comp empty? second)
                                            {:actors          actors
                                             :attack-patterns attack-patterns
                                             :campaigns       campaigns
                                             :coas            coas
                                             :data-tables     data-tables
                                             :exploit-targets exploit-targets
                                             :feedbacks       feedbacks
                                             :incidents       incidents
                                             :indicators      indicators
                                             :judgements      judgements
                                             :malwares        malwares
                                             :relationships   relationships
                                             :sightings       sightings
                                             :tools           tools}))]
                  (if (> (bulk-size bulk) (get-bulk-max-size))
                    (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size)))
                    (-> (gen-bulk-from-fn read-entities bulk identity)
                        ent/un-store-map
                        ok))))))
