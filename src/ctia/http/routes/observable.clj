(ns ctia.http.routes.observable
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities
    [judgement :as judgement]
    [sighting :as sighting]
    [verdict :as verdict]]
   [ctia.http.routes.common :refer [paginated-ok PagingParams]]
   [ctia.lib.pagination :as pag]
   [ctia.properties :refer [properties]]
   [ctia.schemas.core :refer [StoredIndicator
                              StoredJudgement
                              StoredSighting
                              StoredVerdict
                              ObservableTypeIdentifier
                              Reference]]
   [ctia.store :refer :all]
   [ctim.domain.id :as id]
   [ctim.schemas.indicator :as csi]
   [ring.util.http-response :refer [ok not-found]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema JudgementsByObservableQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum
                               :id
                               :disposition
                               :priority
                               :severity
                               :confidence)}))

(s/defschema RefsByObservableQueryParams
  (st/dissoc PagingParams :sort_by :sort_order))

(s/defschema SightingsByObservableQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :timestamp :confidence)}))

(defroutes observable-routes
  (GET "/:observable_type/:observable_value/verdict" []
       :tags ["Verdict"]
       :path-params [observable_type :- ObservableTypeIdentifier
                     observable_value :- s/Str]
       :return (s/maybe StoredVerdict)
       :summary (str "Returns the current Verdict associated with the specified "
                     "observable.")
       :header-params [api_key :- (s/maybe s/Str)]
       :capabilities :read-verdict
       (if-let [d (-> (read-store
                       :verdict list-verdicts
                       {[:observable :type] observable_type
                        [:observable :value] observable_value}
                       {:sort_by :created
                        :sort_order :desc
                        :limit 1})
                      :data
                      first)]
         (ok (verdict/with-long-id d))
         (not-found)))

  (GET "/:observable_type/:observable_value/judgements" []
       :tags ["Judgement"]
       :query [params JudgementsByObservableQueryParams]
       :path-params [observable_type :- ObservableTypeIdentifier
                     observable_value :- s/Str]
       :return (s/maybe [StoredJudgement])
       :summary "Returns the Judgements associated with the specified observable."
       :header-params [api_key :- (s/maybe s/Str)]
       :capabilities :list-judgements
       (paginated-ok
        (judgement/page-with-long-id
         (read-store :judgement
                     list-judgements-by-observable
                     {:type observable_type
                      :value observable_value}
                     params))))

  (GET "/:observable_type/:observable_value/judgements/indicators" []
       :tags ["Indicator"]
       :query [params RefsByObservableQueryParams]
       :path-params [observable_type :- ObservableTypeIdentifier
                     observable_value :- s/Str]
       :return (s/maybe [Reference])
       :summary (str "Returns the Indicator references associated with the "
                     "specified observable based on Judgement relationships.")
       :header-params [api_key :- (s/maybe s/Str)]
       :capabilities #{:list-judgements :list-relationships :list-indicators}
       (paginated-ok
        (let [http-show (get-in @properties [:ctia :http :show])
              judgements (:data (read-store
                                 :judgement
                                 list-judgements-by-observable
                                 {:type observable_type
                                  :value observable_value}
                                 nil))
              judgement-ids (->> judgements
                                 (map :id)
                                 (map #(id/short-id->id :judgement % http-show))
                                 (map id/long-id))
              relationships (:data (read-store
                                    :relationship
                                    list-relationships
                                    {:source_ref judgement-ids
                                     :relationship_type "observable-of"}
                                    nil))
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
       :header-params [api_key :- (s/maybe s/Str)]
       :capabilities :list-sightings
       :return (s/maybe [StoredSighting])
       :summary "Returns Sightings associated with the specified observable."
       (paginated-ok
        (sighting/page-with-long-id
         (read-store :sighting
                     list-sightings-by-observables
                     [{:type observable_type
                       :value observable_value}]
                     params))))

  (GET "/:observable_type/:observable_value/sightings/indicators" []
       :tags ["Indicator"]
       :query [params RefsByObservableQueryParams]
       :path-params [observable_type :- ObservableTypeIdentifier
                     observable_value :- s/Str]
       :return (s/maybe [Reference])
       :summary (str "Returns Indicator references associated with the "
                     "specified observable based on Sighting relationships.")
       :header-params [api_key :- (s/maybe s/Str)]
       :capabilities #{:list-sightings :list-relationships :list-indicators}
       (paginated-ok
        (let [http-show (get-in @properties [:ctia :http :show])
              sightings (:data (read-store :sighting
                                           list-sightings-by-observables
                                           [{:type observable_type
                                             :value observable_value}]
                                           nil))
              sighting-ids (->> sightings
                                (map :id)
                                (map #(id/short-id->id :sighting % http-show))
                                (map id/long-id))
              relationships (:data (read-store
                                    :relationship
                                    list-relationships
                                    {:source_ref sighting-ids
                                     :relationship_type "indicates"}
                                    nil))
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
       :header-params [api_key :- (s/maybe s/Str)]
       :capabilities #{:list-sightings :list-relationships}
       (paginated-ok
        (let [http-show (get-in @properties [:ctia :http :show])
              sightings (:data (read-store :sighting
                                           list-sightings-by-observables
                                           [{:type observable_type
                                             :value observable_value}]
                                           nil))
              sighting-ids (->> sightings
                                (map :id)
                                (map #(id/short-id->id :sighting % http-show))
                                (map id/long-id))
              relationships (:data (read-store
                                    :relationship
                                    list-relationships
                                    {:source_ref sighting-ids
                                     :relationship_type "member-of"}
                                    nil))
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
