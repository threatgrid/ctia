(ns ctia.http.routes.verdict
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.domain.entities.verdict :refer [with-long-id]]
            [ctia.store :refer :all]
            [ctia.schemas.core :refer [ObservableTypeIdentifier
                                       StoredVerdict]]))

(defroutes verdict-routes
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

         (ok (with-long-id d))
         (not-found))))
