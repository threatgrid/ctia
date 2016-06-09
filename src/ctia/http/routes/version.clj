(ns ctia.http.routes.version
  (:require [clojure.java
             [io :as io]
             [shell :as shell]]
            [clojure.string :as st]
            [ctia.domain.entities :refer [schema-version]]
            [ctim.schemas.common :refer [VersionInfo]]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [clojure.string :as st]))

(def version-file "ctia-version.txt")

(def current-version
  (memoize #(if-let [built-version (io/resource version-file)]
              built-version
              (str (:out (shell/sh "git" "log" "-n" "1" "--pretty=format:%H "))
                   (:out (shell/sh "git" "symbolic-ref" "--short" "HEAD"))))))

(defroutes version-routes
  (context "/version" []
    :tags ["version"]
    (GET "/" []
      :return VersionInfo
      :summary "API version details"
      (ok {:base "/ctia"
           :version schema-version
           :beta true
           :build (st/replace (current-version) #"\n" "")
           :supported_features []}))))
