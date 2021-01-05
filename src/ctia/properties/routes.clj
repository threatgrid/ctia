(ns ctia.properties.routes
  (:require [ctia.lib.compojure.api.core :refer [context GET]]
            [ctia.http.routes.common :as routes.common]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [ring.util.http-response :refer [ok]]
            [schema.core :as s]))

(s/defn properties-routes [{{:keys [get-config]} :ConfigService
                            :as _services_} :- APIHandlerServices]
  (let [capabilities :developer]
    (context "/properties" []
             :tags ["Properties"]
             :summary "Configured properties"
             :description (routes.common/capabilities->description capabilities)
             :capabilities capabilities
             (GET "/" []
                  (ok (get-config))))))
