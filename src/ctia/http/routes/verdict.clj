(ns ctia.http.routes.verdict
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.store :refer :all]
            [ctia.http.middleware.cache-control :refer [wrap-cache-control-headers]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
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
    :middleware [wrap-not-modified wrap-cache-control-headers]
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
