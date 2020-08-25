(ns ctia.properties.routes
  (:require [compojure.api.core :refer [context routes GET]]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [ring.util.http-response :refer [ok]]
            [schema.core :as s]))

(s/defn properties-routes [{{:keys [get-config]} :ConfigService
                            :as _services_} :- APIHandlerServices]
 (routes 
  (context "/properties" []
           :tags ["Properties"]
           :summary "Configured properties"
           :capabilities :developer
           (GET "/" []
                (ok (get-config))))))
