(ns ctia.http.routes.version
  (:require [clojure.java
             [io :as io]
             [shell :as shell]]
            [clojure.string :as st]
            [ctia.schemas.common :refer [VersionInfo]]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]))

(defn current-build []
  (if-let [built-version (io/resource "ctia-version.txt")]
    built-version
    (str (:out (shell/sh "git" "log" "-n" "1" "--pretty=format:%H "))
         (:out (shell/sh "git" "symbolic-ref" "--short" "HEAD")))))

(defroutes version-routes
  (context "/version" []
    :tags ["version"]
    (GET "/" []
      :return VersionInfo
      :summary "API version details"
      (ok {:base "/ctia"
           :version "0.1"
           :beta true
           :build (-> (current-build)
                      (st/replace #"\n" ""))
           :supported_features []}))))
