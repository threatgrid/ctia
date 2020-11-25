(ns ctia.ductile-service.core
  (:require [clj-http.client :as client]
            [ctia.ductile-service.schemas]))

(defn request-fn [req]
  (client/request req))
