(ns ctia.observable.core
  (:require [ctia.store
             :refer
             [list-judgements-by-observable
              list-records
              list-sightings-by-observables]]
            [ctim.domain.id :as id]
            [ctia.schemas.core
             :refer
             [APIHandlerServices Observable]]

            [ctia.properties :as p]
            [schema.core :as s]))

(s/defn observable->sightings
  ([observable identity-map services]
   (observable->sightings observable identity-map {} services))
  ([observable :- Observable
    identity-map
    params
    {{:keys [get-store]} :StoreService}]
   (-> (get-store :sighting)
       (list-sightings-by-observables
        [observable]
        identity-map
        params))))

(s/defn observable->judgements
  ([observable identity-map services]
   (observable->judgements observable identity-map {} services))
  ([observable :- Observable
    identity-map
    params
    {{:keys [get-store]} :StoreService}]
   (-> (get-store :judgement)
       (list-judgements-by-observable
        observable
        identity-map
        params))))

(defn observable->sighting-ids
  [observable identity-map services]
  (let [http-show (p/get-http-show services)
        sightings (:data (observable->sightings observable identity-map services))]
    (map #(id/long-id
           (id/short-id->id :sighting (:id %) http-show))
         sightings)))

(defn observable->judgement-ids
  [observable identity-map services]
  (let [http-show (p/get-http-show services)
        judgements (:data (observable->judgements observable identity-map services))]
    (map #(id/long-id
           (id/short-id->id :judgement (:id %) http-show))
         judgements)))

(defn get-relationships
  [filters
   identity-map
   {{:keys [get-store]} :StoreService}]
  (-> (get-store :relationship)
      (list-records
       {:all-of filters}
       identity-map
       {})
      :data))

(s/defschema RelationshipFilter
  {:entity-ids [s/Str]
   :relationship_type [s/Str]
   :entity-type s/Str})

(s/defn related-entity-ids
  [{:keys [entity-ids
           relationship_type
           entity-type]} :- RelationshipFilter
   edge-node :- (s/enum :source_ref :target_ref)
   identity-map
   services]
  (let [filters {edge-node entity-ids
                 :relationship_type relationship_type}
        relationships (get-relationships filters identity-map services)
        get-nodes (case edge-node
                    :source_ref :target_ref
                    :target_ref :source_ref)]
    (->> (map get-nodes relationships)
         (map id/long-id->id)
         (filter #(= (str entity-type) (:type %)))
         (map id/long-id)
         set)))

(s/defn get-target-ids
  [filters :- RelationshipFilter
   identity-map
   services]
  (related-entity-ids filters
                      :source_ref
                      identity-map
                      services))

(s/defn get-source-ids
  [filters :- RelationshipFilter
   identity-map
   services]
  (related-entity-ids filters
                      :target_ref
                      identity-map
                      services))

(s/defn sighting-observable->incident-ids
  [observable :- Observable
   identity-map
   services :- APIHandlerServices]
  (let [sighting-ids (observable->sighting-ids observable identity-map services)]
    (get-target-ids {:entity-ids sighting-ids
                     :relationship_type ["member-of"]
                     :entity-type "incident"}
                    identity-map
                    services)))

(s/defn sighting-observable->indicator-ids
  [observable :- Observable
   identity-map
   services :- APIHandlerServices]
  (let [sighting-ids (observable->sighting-ids observable identity-map services)]
    (get-target-ids {:entity-ids sighting-ids
                     :relationship_type ["sighting-of" "member-of"]
                     :entity-type "indicator"}
                     identity-map
                     services)))

(s/defn judgement-observable->indicator-ids
  [observable :- Observable
   identity-map
   services :- APIHandlerServices]
  (let [judgement-ids (observable->judgement-ids observable identity-map services)]
    (get-target-ids {:entity-ids judgement-ids
                     :relationship_type ["based-on" "element-of"]
                     :entity-type "indicator"}
                    identity-map
                    services)))
