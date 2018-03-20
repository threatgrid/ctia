(ns ctia.http.middleware.version
  (:require [ctia.version :refer [version-headers]]))

(defn wrap-version
  "appends CTIA version headers to response"
  [handler]
  (fn [req]
    (let [{body :body
           :as resp} (handler req)
          version (version-headers)]

      (if body
        (update resp :headers into version)
        resp))))
