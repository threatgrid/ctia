(ns ctia.http.routes.properties
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.properties :refer [properties]]))

(defroutes properties-routes
  (context "/properties" []
           :tags ["properties"]
           (GET "/" []
                (ok @properties))))
