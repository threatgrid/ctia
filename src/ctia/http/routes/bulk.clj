(ns ctia.http.routes.bulk
  (:require [compojure.api.sweet :refer :all]
            [clojure.tools.logging :as log]
            [ctia.domain.entities :as ent]
            [ctia.domain.entities
             [actor :as act-ent]
             [campaign :as cam-ent]
             [coa :as coa-ent]
             [exploit-target :as ept-ent]
             [data-table :as dt-ent]
             [feedback :as fbk-ent]
             [incident :as inc-ent]
             [indicator :as ind-ent]
             [judgement :as jud-ent]
             [relationship :as rel-ent]
             [sighting :as sig-ent]
             [ttp :as ttp-ent]]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common :as common]
            [ctia.lib.keyword :refer [singular]]
            [ctia.schemas.bulk :refer [BulkRefs NewBulk StoredBulk]]
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
    :campaign       ent/realize-campaign
    :coa            ent/realize-coa
    :data-table     ent/realize-data-table
    :exploit-target ent/realize-exploit-target
    :feedback       ent/realize-feedback
    :incident       ent/realize-incident
    :indicator      ent/realize-indicator
    :judgement      ent/realize-judgement
    :relationship   ent/realize-relationship
    :sighting       ent/realize-sighting
    :ttp            ent/realize-ttp))

(defn create-fn
  "return the create function provided an entity type key"
  [k]
  #(write-store k (case k
                    :actor          create-actors
                    :campaign       create-campaigns
                    :coa            create-coas
                    :data-table     create-data-tables
                    :exploit-target create-exploit-targets
                    :feedback       create-feedbacks
                    :incident       create-incidents
                    :indicator      create-indicators
                    :judgement      create-judgements
                    :relationship   create-relationships
                    :sighting       create-sightings
                    :ttp            create-ttps)
                %))

(defn read-fn
  "return the create function provided an entity type key"
  [k]
  #(read-store k (case k
                   :actor          read-actor
                   :campaign       read-campaign
                   :coa            read-coa
                   :data-table     read-data-table
                   :exploit-target read-exploit-target
                   :feedback       read-feedback
                   :incident       read-incident
                   :indicator      read-indicator
                   :judgement      read-judgement
                   :relationship   read-relationship
                   :sighting       read-sighting
                   :ttp            read-ttp)
               %))

(defn with-long-id-fn
  "return the with-long-id function provided an entity type key"
  [k]
  (case k
    :actor          act-ent/with-long-id
    :campaign       cam-ent/with-long-id
    :coa            coa-ent/with-long-id
    :data-table     dt-ent/with-long-id
    :exploit-target ept-ent/with-long-id
    :feedback       fbk-ent/with-long-id
    :incident       inc-ent/with-long-id
    :indicator      ind-ent/with-long-id
    :judgement      jud-ent/with-long-id
    :relationship   rel-ent/with-long-id
    :sighting       sig-ent/with-long-id
    :ttp            ttp-ent/with-long-id))

(defn create-entities
  "Create many entities provided their type and returns a list of ids"
  [entities entity-type login]
  (let [with-long-id (with-long-id-fn entity-type)]
    (->> (flows/create-flow
          :entity-type entity-type
          :realize-fn (realize-fn entity-type)
          :store-fn (create-fn entity-type)
          :identity login
          :entities entities)
         (map with-long-id)
         (map :id))))

(defn read-entities
  "Retrieve many entities of the same type provided their ids and common type"
  [ids entity-type]
  (let [read-entity (read-fn entity-type)
        with-long-id (with-long-id-fn entity-type)]
    (->> ids
         (map (fn [id] (try (with-long-id
                              (read-entity id))
                            (catch Exception e
                              (do (log/error (pr-str e))
                                  nil))))))))

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
                 :header-params [api_key :- (s/maybe s/Str)]
                 :summary "Adds a lot of new entities in only one HTTP call"
                 :capabilities #{:create-actor
                                 :create-campaign
                                 :create-coa
                                 :create-data-table
                                 :create-exploit-target
                                 :create-feedback
                                 :create-incident
                                 :create-indicator
                                 :create-judgement
                                 :create-relationship
                                 :create-sighting
                                 :create-ttp}
                 :identity login
                 (if (> (bulk-size bulk) (get-bulk-max-size))
                   (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size)))
                   (common/created (gen-bulk-from-fn create-entities bulk login))))
           (GET "/" []
                :return (s/maybe StoredBulk)
                :summary "Gets many entities at once"
                :query-params [{actors          :- [Reference] []}
                               {campaigns       :- [Reference] []}
                               {coas            :- [Reference] []}
                               {data-tables     :- [Reference] []}
                               {exploit-targets :- [Reference] []}
                               {feedbacks       :- [Reference] []}
                               {incidents       :- [Reference] []}
                               {indicators      :- [Reference] []}
                               {judgements      :- [Reference] []}
                               {relationships   :- [Reference] []}
                               {sightings       :- [Reference] []}
                               {ttps            :- [Reference] []}]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities #{:read-actor
                                :read-campaign
                                :read-coa
                                :read-data-table
                                :read-exploit-target
                                :read-feedback
                                :read-incident
                                :read-indicator
                                :read-judgement
                                :read-relationship
                                :read-sighting
                                :read-ttp}
                (let [bulk (into {} (remove (comp empty? second)
                                            {:actors          actors
                                             :campaigns       campaigns
                                             :coas            coas
                                             :data-tables     data-tables
                                             :exploit-targets exploit-targets
                                             :feedbacks       feedbacks
                                             :incidents       incidents
                                             :indicators      indicators
                                             :judgements      judgements
                                             :relationships   relationships
                                             :sightings       sightings
                                             :ttps            ttps}))]
                  (if (> (bulk-size bulk) (get-bulk-max-size))
                    (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size)))
                    (ok (gen-bulk-from-fn read-entities bulk)))))))
