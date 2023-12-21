(ns ctia.version.routes
  (:require
   [ctia.lib.compojure.api.core :refer [context GET routes]]
   [ctia.schemas.core :refer [APIHandlerServices VersionInfo]]
   [ctia.version :refer [version-data]]
   [ring.util.http-response :refer [ok]]
   [schema.core :as s]))

(s/defn version-routes [{{:keys [get-in-config]} :ConfigService
                         :as _services_} :- APIHandlerServices]
 (routes
  (context "/version" []
           :tags ["Version"]
           (GET "/" []
                :responses {200 {:schema VersionInfo}}
                :summary "API version details"
                (ok (version-data get-in-config))))))
