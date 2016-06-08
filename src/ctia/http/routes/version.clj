(ns ctia.http.routes.version
  (:require [ctia.version :refer [current-version]]
            [clojure.string :as st]
            [ctim.schemas.common :refer [VersionInfo ctia-schema-version]]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [clojure.string :as st]))

(defroutes version-routes
  (context "/version" []
    :tags ["version"]
    (GET "/" []
      :return VersionInfo
      :summary "API version details"
      (ok {:base "/ctia"
           :version ctia-schema-version
           :beta true
           :build (st/replace (current-version) #"\n" "")
           :supported_features []}))))
