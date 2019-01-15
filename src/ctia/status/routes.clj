(ns ctia.status.routes
  (:require
   [compojure.api.sweet :refer :all]
   [schema.core :as s]
   [ctia.schemas.core :refer [StatusInfo]]
   [ring.util.http-response :refer [ok]]))

(defroutes status-routes
  (context "/status" []
           :tags ["Status"]
           (GET "/" []
                :return StatusInfo
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "Health Check"
                (ok {:status "ok"}))))
