(ns ctia.http.routes.properties
  (:require [compojure.api.sweet :refer :all]
            [ctia.properties :refer [properties]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defroutes properties-routes
  (context "/properties" []
           :tags ["Properties"]
           :summary "The currently running properties"
           :capabilities :admin
           :header-params [api_key :- (s/maybe s/Str)]
           (GET "/" []
                (ok @properties))))
