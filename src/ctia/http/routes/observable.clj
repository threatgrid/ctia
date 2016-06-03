(ns ctia.http.routes.observable
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.lib.pagination :as pag]
   [ctia.http.routes.common :refer [paginated-ok PagingParams]]
   [ctim.schemas
    [common :as c]
    [judgement :refer [StoredJudgement]]
    [sighting :refer [StoredSighting]]
    [vocabularies :refer [ObservableType]]]
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
    :path-params [observable_type :- ObservableType
                  observable_value :- s/Str]
    :return (s/maybe [StoredJudgement])
    :summary "Returns all the Judgements associated with the specified observable."
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-judgements
    (paginated-ok (list-judgements-by-observable @judgement-store
                                                 {:type observable_type
                                                  :value observable_value} params)))

  (GET "/:observable_type/:observable_value/indicators" []
    :tags ["Indicator"]
    :query [params IndicatorRefsByObservableQueryParams]
    :path-params [observable_type :- ObservableType
                  observable_value :- s/Str]
    :return (s/maybe [c/Reference])
    :summary "Returns all the Indicator References associated with the specified observable."
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities #{:list-judgements :list-indicators}
    (paginated-ok
     (let [res (->> (list-judgements-by-observable @judgement-store
                                                   {:type observable_type
                                                    :value observable_value}
                                                   nil)
                    :data
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
    :path-params [observable_type :- ObservableType
                  observable_value :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :list-sightings
    :return (s/maybe [StoredSighting])
    :summary "Returns all the Sightings associated with the specified observable."
    (paginated-ok (list-sightings-by-observables @sighting-store
                                                 [{:type observable_type
                                                   :value observable_value}] params))))
