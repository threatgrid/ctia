(ns ctia.http.routes.observable
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities
    [judgement :as judgement]
    [sighting :as sighting]]
   [ctia.lib.pagination :as pag]
   [ctia.http.routes.common :refer [paginated-ok PagingParams]]
   [ctia.schemas.core :refer [StoredJudgement
                              StoredSighting
                              ObservableTypeIdentifier
                              Reference]]
   [ctia.store :refer :all]
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

(s/defschema IndicatorRefsByObservableQueryParams
  (st/dissoc PagingParams :sort_by :sort_order))

(s/defschema SightingsByObservableQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :timestamp :confidence)}))

(defroutes observable-routes
  (GET "/:observable_type/:observable_value/judgements" []
       :tags ["Judgement"]
       :query [params JudgementsByObservableQueryParams]
       :path-params [observable_type :- ObservableTypeIdentifier
                     observable_value :- s/Str]
       :return (s/maybe [StoredJudgement])
       :summary "Returns all the Judgements associated with the specified observable."
       :header-params [api_key :- (s/maybe s/Str)]
       :capabilities :list-judgements
       (paginated-ok
        (judgement/page-with-long-id
         (read-store :judgement list-judgements-by-observable {:type observable_type
                                                               :value observable_value} params))))

  (GET "/:observable_type/:observable_value/indicators" []
       :tags ["Indicator"]
       :query [params IndicatorRefsByObservableQueryParams]
       :path-params [observable_type :- ObservableTypeIdentifier
                     observable_value :- s/Str]
       :return (s/maybe [Reference])
       :summary "Returns all the Indicator References associated with the specified observable."
       :header-params [api_key :- (s/maybe s/Str)]
       :capabilities #{:list-judgements :list-indicators}
       (paginated-ok
        (let [query-res (:data (read-store
                                :judgement
                                list-judgements-by-observable {:type observable_type
                                                               :value observable_value} nil))
              res (->> query-res
                       (mapcat :indicators)
                       (map :indicator_id)
                       distinct
                       sort)]
          (-> res
              (pag/paginate params)
              (pag/response (:offset params)
                            (:limit params)
                            (count res))))))

  (GET "/:observable_type/:observable_value/sightings" []
       :tags ["Sighting"]
       :query [params SightingsByObservableQueryParams]
       :path-params [observable_type :- ObservableTypeIdentifier
                     observable_value :- s/Str]
       :header-params [api_key :- (s/maybe s/Str)]
       :capabilities :list-sightings
       :return (s/maybe [StoredSighting])
       :summary "Returns all the Sightings associated with the specified observable."
       (paginated-ok
        (sighting/page-with-long-id
         (read-store :sighting list-sightings-by-observables [{:type observable_type
                                                               :value observable_value}] params)))))
