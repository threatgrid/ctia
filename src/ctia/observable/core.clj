(ns ctia.observable.core
  (:require [ctia.store
             :refer
             [calculate-verdict
              list-judgements-by-observable
              list-records
              list-sightings-by-observables]]
            [ctim.domain.id :as id]
            [ctia.schemas.core
             :refer
             [APIHandlerServices Observable Reference Verdict]]

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

(s/defn related-entity-ids
  [filters
   edge-end :- (s/enum :source_ref :target_ref)
   entity-type
   identity-map
   services]
  (let [relationships (get-relationships filters identity-map services)]
    (->> (map edge-end relationships)
         (map #(id/long-id->id %))
         (filter #(= (str entity-type) (:type %)))
         (map #(id/long-id %))
         set)))

(s/defn get-target-ids
  [entity-ids
   relationship_type
   entity-type
   identity-map
   services]
  (related-entity-ids {:source_ref entity-ids
                       :relationship_type relationship_type}
                      :target_ref
                      entity-type
                      identity-map
                      services))

(s/defn get-source-ids
  [entity-ids
   relationship_type
   entity-type
   identity-map
   services]
  (related-entity-ids {:target_ref entity-ids
                       :relationship_type relationship_type}
                      :source_ref
                      entity-type
                      identity-map
                      services))

(s/defn sighting-observable->incident-ids
  [observable :- Observable
   identity-map
   services :- APIHandlerServices]
  (let [sighting-ids (observable->sighting-ids observable identity-map services)]
    (get-target-ids sighting-ids
                    "member-of"
                    "incident"
                    identity-map
                    services)))

(s/defn sighting-observable->indicator-ids
  [observable :- Observable
   identity-map
   services :- APIHandlerServices]
  (let [sighting-ids (observable->sighting-ids observable identity-map services)]
    (get-target-ids sighting-ids
                    #{"sighting-of" "member-of"}
                    "indicator"
                    identity-map
                    services)))

(s/defn judgement-observable->indicator-ids
  [observable :- Observable
   identity-map
   services :- APIHandlerServices]
  (let [judgement-ids (observable->judgement-ids observable identity-map services)]
    (get-target-ids judgement-ids
                    #{"based-on" "element-of"}
                    "indicator"
                    identity-map
                    services)))
