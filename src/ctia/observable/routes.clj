(ns ctia.observable.routes
  (:require [compojure.api.core :refer [GET routes]]
            [ctia.domain.entities
             :refer
             [page-with-long-id short-id->long-id un-store-page]]
            [ctia.entity.judgement :refer [JudgementsByObservableQueryParams]]
            [ctia.entity.judgement.schemas :refer [PartialJudgementList]]
            [ctia.entity.sighting :refer [SightingsByObservableQueryParams]]
            [ctia.entity.sighting.schemas :refer [PartialSightingList]]
            [ctia.http.routes.common :refer [paginated-ok PagingParams]]
            [ctia.properties :as p]
            [ctia.schemas.core
             :refer
             [APIHandlerServices ObservableTypeIdentifier Reference Verdict]]
            [ctia.store
             :refer
             [calculate-verdict
              list-judgements-by-observable
              list-records
              list-sightings-by-observables]]
            [ctim.domain.id :as id]
            [ductile.pagination :as pag]
            [ring.swagger.schema :refer [describe]]
            [ring.util.http-response :refer [not-found ok]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defschema RefsByObservableQueryParams
  (st/assoc PagingParams
            (s/optional-key :sort_by)
            (describe (s/enum :id) "Sort result on a field")))

(s/defn observable-routes [{{:keys [read-store]} :StoreService
                            :as services} :- APIHandlerServices]
  (routes
   (GET "/:observable_type/:observable_value/verdict" []
     :tags ["Verdict"]
     :path-params [observable_type :- ObservableTypeIdentifier
                   observable_value :- s/Str]
     :return (s/maybe Verdict)
     :summary (str "Returns the current Verdict associated with the specified "
                   "observable.")
     :capabilities :read-verdict
     :auth-identity identity
     :identity-map identity-map
     (or (some-> (read-store :judgement
                             calculate-verdict
                             {:type observable_type
                              :value observable_value}
                             identity-map)
                 (update :judgement_id short-id->long-id services)
                 ok)
         (not-found {:message "no verdict currently available for the supplied observable"})))

   (GET "/:observable_type/:observable_value/judgements" []
     :tags ["Judgement"]
     :query [params JudgementsByObservableQueryParams]
     :path-params [observable_type :- ObservableTypeIdentifier
                   observable_value :- s/Str]
     :return PartialJudgementList
     :summary "Returns the Judgements associated with the specified observable."
     :capabilities :list-judgements
     :auth-identity identity
     :identity-map identity-map
     (-> (read-store :judgement
                     list-judgements-by-observable
                     {:type observable_type
                      :value observable_value}
                     identity-map
                     params)
         (page-with-long-id services)
         un-store-page
         paginated-ok))

   (GET "/:observable_type/:observable_value/judgements/indicators" []
     :tags ["Indicator"]
     :query [params RefsByObservableQueryParams]
     :path-params [observable_type :- ObservableTypeIdentifier
                   observable_value :- s/Str]
     :return (s/maybe [Reference])
     :summary (str "Returns the Indicator references associated with the "
                   "specified observable based on Judgement relationships.")
     :capabilities #{:list-judgements :list-relationships}
     :auth-identity identity
     :identity-map identity-map
     (paginated-ok
      (let [http-show (p/get-http-show services)
            judgements (:data (read-store
                               :judgement
                               list-judgements-by-observable
                               {:type observable_type
                                :value observable_value}
                               identity-map
                               {:fields [:id]}))
            judgement-ids (->> judgements
                               (map :id)
                               (map #(id/short-id->id :judgement % http-show))
                               (map id/long-id))
            relationships (:data (read-store
                                  :relationship
                                  list-records
                                  {:all-of {:source_ref judgement-ids}}
                                  identity-map
                                  {:fields [:target_ref]}))
            indicator-ids (->> (map :target_ref relationships)
                               (map #(id/long-id->id %))
                               (filter #(= "indicator" (:type %)))
                               (map #(id/long-id %))
                               set)]
        (-> indicator-ids
            (pag/paginate params)
            (pag/response {:offset (:offset params)
                           :limit (:limit params)
                           :total-hits (count indicator-ids)})))))

   (GET "/:observable_type/:observable_value/sightings" []
     :tags ["Sighting"]
     :query [params SightingsByObservableQueryParams]
     :path-params [observable_type :- ObservableTypeIdentifier
                   observable_value :- s/Str]
     :capabilities :list-sightings
     :auth-identity identity
     :identity-map identity-map
     :return PartialSightingList
     :summary "Returns Sightings associated with the specified observable."
     (-> (read-store :sighting
                     list-sightings-by-observables
                     [{:type observable_type
                       :value observable_value}]
                     identity-map
                     params)
         (page-with-long-id services)
         un-store-page
         paginated-ok))

   (GET "/:observable_type/:observable_value/sightings/indicators" []
     :tags ["Indicator"]
     :query [params RefsByObservableQueryParams]
     :path-params [observable_type :- ObservableTypeIdentifier
                   observable_value :- s/Str]
     :return (s/maybe [Reference])
     :summary (str "Returns Indicator references associated with the "
                   "specified observable based on Sighting relationships.")
     :capabilities #{:list-sightings :list-relationships}
     :auth-identity identity
     :identity-map identity-map
     (paginated-ok
      (let [http-show (p/get-http-show services)
            sightings (:data (read-store :sighting
                                         list-sightings-by-observables
                                         [{:type observable_type
                                           :value observable_value}]
                                         identity-map
                                         {:fields [:id]}))
            sighting-ids (->> sightings
                              (map :id)
                              (map #(id/short-id->id :sighting % http-show))
                              (map id/long-id))
            relationships (:data (read-store
                                  :relationship
                                  list-records
                                  {:all-of {:source_ref sighting-ids}}
                                  identity-map
                                  {:fields [:target_ref]}))
            indicator-ids (->> (map :target_ref relationships)
                               (map #(id/long-id->id %))
                               (filter #(= "indicator" (:type %)))
                               (map #(id/long-id %))
                               set)]
        (-> indicator-ids
            (pag/paginate params)
            (pag/response {:offset (:offset params)
                           :limit (:limit params)
                           :total-hits (count indicator-ids)})))))

   (GET "/:observable_type/:observable_value/sightings/incidents" []
     :tags ["Incident"]
     :query [params RefsByObservableQueryParams]
     :path-params [observable_type :- ObservableTypeIdentifier
                   observable_value :- s/Str]
     :return (s/maybe [Reference])
     :summary (str "Returns Incident references associated with the "
                   "specified observable based on Sighting relationships")
     :capabilities #{:list-sightings :list-relationships}
     :auth-identity identity
     :identity-map identity-map
     (paginated-ok
      (let [http-show (p/get-http-show services)
            sightings (:data (read-store :sighting
                                         list-sightings-by-observables
                                         [{:type observable_type
                                           :value observable_value}]
                                         identity-map
                                         {:fields [:id]}))
            sighting-ids (->> sightings
                              (map :id)
                              (map #(id/short-id->id :sighting % http-show))
                              (map id/long-id))
            relationships (:data (read-store
                                  :relationship
                                  list-records
                                  {:all-of {:source_ref sighting-ids}}
                                  identity-map
                                  {:fields [:target_ref]}))
            incident-ids (->> (map :target_ref relationships)
                              (map #(id/long-id->id %))
                              (filter #(= "incident" (:type %)))
                              (map #(id/long-id %))
                              set)]
        (-> incident-ids
            (pag/paginate params)
            (pag/response {:offset (:offset params)
                           :limit (:limit params)
                           :total-hits (count incident-ids)})))))))
