(ns ctia.status.routes
  (:require
   [compojure.api.core :refer [context GET]]
   [schema.core :as s]
   [ctia.schemas.core :refer [StatusInfo]]
   [ring.util.http-response :refer [ok]]))

(defn status-routes []
  (routes
    (context "/status" []
             :tags ["Status"]
             (GET "/" []
                  :return StatusInfo
                  :summary "Health Check"
                  (ok {:status :ok})))))
