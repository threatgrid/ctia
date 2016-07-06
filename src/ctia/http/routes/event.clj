(ns ctia.http.routes.event
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.events :refer [recent-events]]
            [ctia.http.middleware.cache-control :refer [wrap-cache-control-headers]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ctim.events.schemas :refer [ModelEventBase]]))

(defroutes event-routes
  (context "/events" []
    :tags ["Events"]
    (GET "/log" []
      :return [ModelEventBase]
      :summary "Recent Event log"
      :capabilities :developer
      :middleware [wrap-not-modified wrap-cache-control-headers]
      (ok (recent-events)))))
