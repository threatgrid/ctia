(ns ctia.properties.routes
  (:require [compojure.api.core :refer [context routes GET]]
            [ctia.properties :as p]
            [ring.util.http-response :refer [ok]]
            [schema.core :as s]))

(defn properties-routes [{{:keys [get-config]} :ConfigService
                          :as _services_}]
 (routes 
  (context "/properties" []
           :tags ["Properties"]
           :summary "Configured properties"
           :capabilities :developer
           (GET "/" []
                (ok (get-config))))))
