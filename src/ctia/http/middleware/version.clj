(ns ctia.http.middleware.version
  (:require [ctia.version :refer [version-headers]]))

(defn wrap-version
  "appends CTIA version headers to response"
  [handler get-in-config]
  (fn [req]
    (let [{body :body
           :as resp} (handler req)
          version (version-headers get-in-config)]

      (if body
        (update resp :headers into version)
        resp))))
