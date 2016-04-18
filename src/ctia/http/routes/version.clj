(ns ctia.http.routes.version
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.schemas.common :refer [VersionInfo]]))

(defroutes version-routes
  (context "/version" []
    :tags ["version"]
    (GET "/" []
      :return VersionInfo
      :summary "API version details"
      (ok {:base "/ctia"
           :version "0.1"
           :beta true
           :supported_features []}))))
