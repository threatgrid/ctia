(ns ctia.http.routes.event
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.events.schemas :refer [ModelEventBase]]
            [ctia.events :refer [recent-events]]))

(defroutes event-routes
  (context "/events" []
    :tags ["Events"]
    (GET "/log" []
      :return [ModelEventBase]
      :summary "Recent Event log"
      :capabilities #{:admin}
      (ok (recent-events)))))
