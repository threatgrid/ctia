(ns ctia.status.routes
  (:require
   [ctia.lib.compojure.api.core :refer [context GET]]
   [ctia.schemas.core :refer [StatusInfo]]
   [ring.util.http-response :refer [ok]]))

(defn status-routes []
  (context "/status" []
           :tags ["Status"]
           (GET "/" []
                :return StatusInfo
                :summary "Health Check"
                (ok {:status :ok}))))
