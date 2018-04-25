(ns ctia.observable.routes
  (:require
   [compojure.api.sweet :refer :all]
   [ctia
    [properties :refer [properties]]
    [store :refer :all]]
   [ctia.domain.entities
    :refer
    [page-with-long-id short-id->long-id un-store-page]]
   [ctia.entity
    [judgement :refer [JudgementsByObservableQueryParams]]
    [sighting :refer [SightingsByObservableQueryParams]]]
   [ctia.entity.judgement.schemas :refer [PartialJudgementList]]
   [ctia.entity.sighting.schemas :refer [PartialSightingList]]
   [ctia.http.routes.common :refer [paginated-ok PagingParams]]
   [ctia.lib.pagination :as pag]
   [ctia.schemas.core :refer [ObservableTypeIdentifier Reference Verdict]]
   [ctim.domain.id :as id]
   [ring.util.http-response :refer [not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema RefsByObservableQueryParams
  (st/dissoc PagingParams :sort_by :sort_order))

(defroutes observable-routes
  (GET "/:observable_type/:observable_value/verdict" []
       :tags ["Verdict"]
       :path-params [observable_type :- ObservableTypeIdentifier
                     observable_value :- s/Str]
       :return (s/maybe Verdict)
       :summary (str "Returns the current Verdict associated with the specified "
                     "observable.")
       :header-params [{Authorization :- (s/maybe s/Str) nil}]
       :capabilities :read-verdict
       :auth-identity identity
       :identity-map identity-map
       (or (some-> (read-store :judgement
                               calculate-verdict
                               {:type observable_type
                                :value observable_value}
                               identity-map)
                   (clojure.core/update :judgement_id short-id->long-id)
                   ok)
           (not-found)))

  (GET "/:observable_type/:observable_value/judgements" []
       :tags ["Judgement"]
       :query [params JudgementsByObservableQueryParams]
       :path-params [observable_type :- ObservableTypeIdentifier
                     observable_value :- s/Str]
       :return PartialJudgementList
       :summary "Returns the Judgements associated with the specified observable."
       :header-params [{Authorization :- (s/maybe s/Str) nil}]
       :capabilities :list-judgements
       :auth-identity identity
       :identity-map identity-map
       (-> (read-store :judgement
                       list-judgements-by-observable
                       {:type observable_type
                        :value observable_value}
                       identity-map
                       params)
           page-with-long-id
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
       :header-params [{Authorization :- (s/maybe s/Str) nil}]
       :capabilities #{:list-judgements :list-relationships}
       :auth-identity identity
       :identity-map identity-map
       (paginated-ok
        (let [http-show (get-in @properties [:ctia :http :show])
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
                                    {:source_ref judgement-ids}
                                    identity-map
                                    {:fields [:target_ref]}))
              indicator-ids (->> (map :target_ref relationships)
                                 (map #(id/long-id->id %))
                                 (filter #(= "indicator" (:type %)))
                                 (map #(id/long-id %))
                                 set)]
          (-> indicator-ids
              (pag/paginate params)
              (pag/response (:offset params)
                            (:limit params)
                            (count indicator-ids))))))

  (GET "/:observable_type/:observable_value/sightings" []
       :tags ["Sighting"]
       :query [params SightingsByObservableQueryParams]
       :path-params [observable_type :- ObservableTypeIdentifier
                     observable_value :- s/Str]
       :header-params [{Authorization :- (s/maybe s/Str) nil}]
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
           page-with-long-id
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
       :header-params [{Authorization :- (s/maybe s/Str) nil}]
       :capabilities #{:list-sightings :list-relationships}
       :auth-identity identity
       :identity-map identity-map
       (paginated-ok
        (let [http-show (get-in @properties [:ctia :http :show])
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
                                    {:source_ref sighting-ids}
                                    identity-map
                                    {:fields [:target_ref]}))
              indicator-ids (->> (map :target_ref relationships)
                                 (map #(id/long-id->id %))
                                 (filter #(= "indicator" (:type %)))
                                 (map #(id/long-id %))
                                 set)]
          (-> indicator-ids
              (pag/paginate params)
              (pag/response (:offset params)
                            (:limit params)
                            (count indicator-ids))))))

  (GET "/:observable_type/:observable_value/sightings/incidents" []
       :tags ["Incident"]
       :query [params RefsByObservableQueryParams]
       :path-params [observable_type :- ObservableTypeIdentifier
                     observable_value :- s/Str]
       :return (s/maybe [Reference])
       :summary (str "Returns Incident references associated with the "
                     "specified observable based on Sighting relationships")
       :header-params [{Authorization :- (s/maybe s/Str) nil}]
       :capabilities #{:list-sightings :list-relationships}
       :auth-identity identity
       :identity-map identity-map
       (paginated-ok
        (let [http-show (get-in @properties [:ctia :http :show])
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
                                    {:source_ref sighting-ids}
                                    identity-map
                                    {:fields [:target_ref]}))
              incident-ids (->> (map :target_ref relationships)
                                (map #(id/long-id->id %))
                                (filter #(= "incident" (:type %)))
                                (map #(id/long-id %))
                                set)]
          (-> incident-ids
              (pag/paginate params)
              (pag/response (:offset params)
                            (:limit params)
                            (count incident-ids)))))))
