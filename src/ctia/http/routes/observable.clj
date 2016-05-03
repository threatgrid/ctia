(ns ctia.http.routes.observable
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.store :refer :all]
            [ctia.schemas.vocabularies :refer [ObservableType]]
            [ctia.schemas.indicator :refer [StoredIndicator]]
            [ctia.schemas.sighting :refer [StoredSighting]]
            [ctia.schemas.judgement :refer [StoredJudgement]]
            [ctia.schemas.verdict :refer [Verdict]]
            [ctia.schemas.common :refer [DispositionName
                                         DispositionNumber
                                         Time]]))

(def JudgementSort
  "A sort ordering"
  (s/enum "disposition" "timestamp" "priority" "severity" "confidence"))

(def IndicatorSort
  "A sort ordering"
  (s/enum "title" "timestamp"))

(def RelationSort
  "A sort ordering"
  (s/enum "relation" "related" "related_type" "timestamp"))

(def SortOrder
  (s/enum "asc" "desc"))

(defroutes observable-routes
  (GET "/:observable_type/:observable_value/judgements" []
    :tags ["Judgement"]
    :query-params [{offset :-  Long 0}
                   {limit :-  Long 0}
                   {after :-  Time nil}
                   {before :-  Time nil}
                   {sort_by :- JudgementSort "timestamp"}
                   {sort_order :- SortOrder "desc"}
                   {source :- s/Str nil}
                   {disposition :- DispositionNumber nil}
                   {disposition_name :- DispositionName nil}]
    :path-params [observable_type :- ObservableType
                  observable_value :- s/Str]
    :return (s/maybe [StoredJudgement])
    :summary "Returns all the Judgements associated with the specified observable."
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities #{:list-judgements-by-observable :admin}
    (ok (list-judgements-by-observable @judgement-store
                                       {:type observable_type
                                        :value observable_value})))

  (GET "/:observable_type/:observable_value/indicators" []
    :tags ["Indicator"]
    :query-params [{offset :-  Long 0}
                   {limit :-  Long 0}
                   {after :-  Time nil}
                   {before :-  Time nil}
                   {sort_by :- JudgementSort "timestamp"}
                   {sort_order :- SortOrder "desc"}
                   {source :- s/Str nil}]
    :path-params [observable_type :- ObservableType
                  observable_value :- s/Str]
    :return (s/maybe [StoredIndicator])
    :summary "Returns all the Indicators associated with the specified observable."
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities #{:list-indicators-by-observable :admin}
    (ok (some->> {:type observable_type
                  :value observable_value}
                 (list-judgements-by-observable @judgement-store)
                 (list-indicators-by-judgements @indicator-store))))

  (GET "/:observable_type/:observable_value/sightings" []
    :tags ["Sighting"]
    :query-params [{offset :-  Long 0}
                   {limit :-  Long 0}
                   {after :-  Time nil}
                   {before :-  Time nil}
                   {sort_by :- JudgementSort "timestamp"}
                   {sort_order :- SortOrder "desc"}
                   {source :- s/Str nil}]
    :path-params [observable_type :- ObservableType
                  observable_value :- s/Str]
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities #{:list-sightings-by-observable :admin}
    :return (s/maybe [StoredSighting])
    :summary "Returns all the Sightings associated with the specified observable."
    (ok (list-sightings-by-observables @sighting-store
                                       [{:type observable_type
                                         :value observable_value}]))))
