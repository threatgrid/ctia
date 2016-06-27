(ns ctia.http.routes.bulk
  (:require [compojure.api.sweet :refer :all]
            [clojure.tools.logging :as log]
            [ctia.domain.entities :as ent]
            [ctia.flows.crud :as flows]
            [ctia.lib.keyword :refer [singular]]
            [ctia.schemas.bulk :refer [BulkRefs NewBulk StoredBulk]]
            [ctia.properties :refer [properties]]
            [ctia.store :refer :all]
            [ctim.schemas.common :as c]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defn realize
  "return the realize function provided an entity key name"
  [k]
  (condp = k
    :actor          ent/realize-actor
    :campaign       ent/realize-campaign
    :coa            ent/realize-coa
    :exploit-target ent/realize-exploit-target
    :feedback       ent/realize-feedback
    :incident       ent/realize-incident
    :indicator      ent/realize-indicator
    :judgement      ent/realize-judgement
    :sighting       ent/realize-sighting
    :ttp            ent/realize-ttp))

(defn create-fn
  "return the create function provided an entity key name"
  [k]
  #(write-store k (case k
                    :actor          create-actor
                    :campaign       create-campaign
                    :coa            create-coa
                    :exploit-target create-exploit-target
                    :feedback       create-feedback
                    :incident       create-incident
                    :indicator      create-indicator
                    :judgement      create-judgement
                    :sighting       create-sighting
                    :ttp            create-ttp) %))

(defn read-fn
  "return the create function provided an entity key name"
  [k]
  #(read-store k (case k
                   :actor          read-actor
                   :campaign       read-campaign
                   :coa            read-coa
                   :exploit-target read-exploit-target
                   :feedback       read-feedback
                   :incident       read-incident
                   :indicator      read-indicator
                   :judgement      read-judgement
                   :sighting       read-sighting
                   :ttp            read-ttp) %))

(defn create-entities
  "Create many entities provided their type and returns a list of ids"
  [entities entity-type login]
  (->> entities
       (map #(try (flows/create-flow
                   :entity-type entity-type
                   :realize-fn (realize entity-type)
                   :store-fn (create-fn entity-type)
                   :identity login
                   :entity %)
                  (catch Exception e
                    (do (log/error (pr-str e))
                        nil))))
       (map :id)))

(defn read-entities
  "Retrieve many entities of the same type provided their ids and common type"
  [ids entity-type]
  (let [read-entity (read-fn entity-type)]
    (->> ids
         (map (fn [id] (try (read-entity id)
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
  (get-in @properties
          [:ctia :http :bulk :max-size]
          2000))

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
                      :create-exploit-target
                      :create-feedback
                      :create-incident
                      :create-indicator
                      :create-judgement
                      :create-sighting
                      :create-ttp}
      :identity login
      (if (> (bulk-size bulk) (get-bulk-max-size))
        (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size)))
        (created (gen-bulk-from-fn create-entities bulk login))))
    (GET "/" []
      :return (s/maybe StoredBulk)
      :summary "Gets many entities at once"
      :query-params [{actors          :- [c/Reference] []}
                     {campaigns       :- [c/Reference] []}
                     {coas            :- [c/Reference] []}
                     {exploit-targets :- [c/Reference] []}
                     {feedbacks       :- [c/Reference] []}
                     {incidents       :- [c/Reference] []}
                     {indicators      :- [c/Reference] []}
                     {judgements      :- [c/Reference] []}
                     {sightings       :- [c/Reference] []}
                     {ttps            :- [c/Reference] []}]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:read-actor
                      :read-campaign
                      :read-coa
                      :read-exploit-target
                      :read-feedback
                      :read-incident
                      :read-indicator
                      :read-judgement
                      :read-sighting
                      :read-ttp}
      (let [bulk (into {} (remove (comp empty? second)
                                  {:actors          actors
                                   :campaigns       campaigns
                                   :coas            coas
                                   :exploit-targets exploit-targets
                                   :feedbacks       feedbacks
                                   :incidents       incidents
                                   :indicators      indicators
                                   :judgements      judgements
                                   :sightings       sightings
                                   :ttps            ttps}))]
        (if (> (bulk-size bulk) (get-bulk-max-size))
          (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size)))
          (ok (gen-bulk-from-fn read-entities bulk)))))))
