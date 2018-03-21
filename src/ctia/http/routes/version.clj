(ns ctia.http.routes.version
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.schemas.core :refer [VersionInfo]]
   [ctia.version :refer [version-data]]
   [ring.util.http-response :refer :all]))

(defroutes version-routes
  (context "/version" []
           :tags ["Version"]
           (GET "/" []
                :return VersionInfo
                :summary "API version details"
                (ok (version-data)))))
