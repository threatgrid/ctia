(ns ctia.version.routes
  (:require
   [compojure.api.core :refer [context GET routes]]
   [ctia.schemas.core :refer [VersionInfo]]
   [ctia.version :refer [version-data]]
   [ring.util.http-response :refer [ok]]))

(defn version-routes [get-in-config]
 (routes
  (context "/version" []
           :tags ["Version"]
           (GET "/" []
                :return VersionInfo
                :summary "API version details"
                (ok (version-data get-in-config))))))
