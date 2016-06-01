(ns ctia.http.routes.verdict
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.store :refer :all]
            [ctim.schemas
             [vocabularies :refer [ObservableType]]
             [verdict :refer [Verdict]]]))

(defroutes verdict-routes
  (GET "/:observable_type/:observable_value/verdict" []
    :tags ["Verdict"]
    :path-params [observable_type :- ObservableType
                  observable_value :- s/Str]
    :return (s/maybe Verdict)
    :summary "Returns the current Verdict associated with the specified observable."
    :header-params [api_key :- (s/maybe s/Str)]
    :capabilities :read-verdict
    (if-let [d (read-store :judgement
                           (fn [store]
                             (calculate-verdict store {:type observable_type
                                                       :value observable_value})))]
      (ok d)
      (not-found))))
