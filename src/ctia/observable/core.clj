(ns ctia.observable.core
  (:require [ctia.store
             :refer
             [calculate-verdict
              list-judgements-by-observable
              list-records
              list-sightings-by-observables]]
            [ctia.domain.entities
             :refer
             [page-with-long-id short-id->long-id un-store-page]]
            [ctim.domain.id :as id]
            [ctia.schemas.core
             :refer
             [APIHandlerServices Observable Reference Verdict]]

            [ctia.properties :as p]
            [schema.core :as s]))

(s/defn observables->sightings
  [observables :- [Observable]
   identity-map
   {{:keys [get-store]} :StoreService}]
  (-> (get-store :sighting)
      (list-sightings-by-observables
       observables
       identity-map
       {})
      :data))

(defn observables->sighting-ids
  [observables identity-map services]
  (let [http-show (p/get-http-show services)
        sightings (observables->sightings observables identity-map services)]
    (map #(id/long-id
          (id/short-id->id :sighting (:id %) http-show))
         sightings)))

(s/defn observables->incident-ids
  [observables :- [Observable]
   identity-map
   {{:keys [get-store]} :StoreService
    :as services} :- APIHandlerServices]
  (let [sighting-ids (observables->sighting-ids observables identity-map services)
        relationships (-> (get-store :relationship)
                          (list-records
                           {:all-of {:source_ref sighting-ids}}
                           identity-map
                           {:fields [:target_ref]})
                          :data)
        incident-ids (->> (map :target_ref relationships)
                          (map #(id/long-id->id %))
                          (filter #(= "incident" (:type %)))
                          (map #(id/long-id %))
                          set)]
    incident-ids))
