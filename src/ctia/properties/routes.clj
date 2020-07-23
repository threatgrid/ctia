(ns ctia.properties.routes
  (:require [compojure.api.sweet :refer :all]
            [ctia.properties :as p]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defroutes properties-routes
  (context "/properties" []
           :tags ["Properties"]
           :summary "Configured properties"
           :capabilities :developer
           (GET "/" []
                (ok (p/read-global-properties)))))
