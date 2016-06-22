(ns ctia.http.routes.verdict
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.store :refer :all]
            [ctim.schemas
             [vocabularies :refer [ObservableTypeIdentifier]]
             [verdict :refer [StoredVerdict]]]))

(defroutes verdict-routes
  (GET "/:observable_type/:observable_value/verdict" []
    :tags ["Verdict"]
    :path-params [observable_type :- ObservableTypeIdentifier
                  observable_value :- s/Str]
    :return (s/maybe StoredVerdict)
    :summary "Returns the current Verdict associated with the specified observable."
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :read-verdict
    (if-let [d (-> (read-store :verdict list-verdicts
                               {[:observable :type] observable_type
                                [:observable :value] observable_value} {:sort_by :created
                                                                        :sort_order :desc
                                                                        :limit 1})
                   :data
                   first)]

      (ok d)
      (not-found))))
