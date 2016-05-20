(ns ctia.http.routes.observable
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.http.routes.common :refer [paginated-ok PagingParams]]
   [ctia.schemas
    [indicator :refer [StoredIndicator]]
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

(s/defschema IndicatorsByObservableQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :title)}))

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
    :query [params IndicatorsByObservableQueryParams]
    :path-params [observable_type :- ObservableType
                  observable_value :- s/Str]
    :return (s/maybe [StoredIndicator])
    :summary "Returns all the Indicators associated with the specified observable."
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities #{:list-judgements :list-indicators}
    (paginated-ok
     (as-> {:type observable_type
            :value observable_value} $
       (list-judgements-by-observable @judgement-store $ nil)
       (:data $ [])
       (list-indicators-by-judgements @indicator-store $ params))))

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
