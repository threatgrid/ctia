(ns ctia.http.middleware.otel
  "Ring middleware that sets the OTel http.route span attribute by
  matching the request URI against a pre-compiled route table.
  Works for all responses including those short-circuited by auth
  middleware (401s), unlike compojure.core/wrap-routes which only
  fires at leaf route handlers."
  (:require [clojure.tools.logging :as log]
            [clout.core :as clout]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]))

(defn compile-route-table
  "Compile a route table (from compojure-api's get-routes) into a map
  of HTTP verb to vector of [compiled-path template] tuples."
  [route-table]
  (-> (group-by (fn [[_ method]] (name method)) route-table)
      (update-vals (fn [entries]
                     (mapv (fn [[path _]] [(clout/route-compile path) path])
                           entries)))))

(defn find-route-template
  "Return the route template matching `request`, or nil if none matches."
  [compiled-routes request]
  (when-let [method (:request-method request)]
    (some (fn [[compiled-path template]]
            (when (clout/route-matches compiled-path request)
              template))
          (get compiled-routes (name method)))))

(defn wrap-otel-route
  "Wraps a handler to set the OTel http.route span attribute.
  `route-table` is the result of compojure.api.routes/get-routes."
  [handler route-table]
  (let [compiled (compile-route-table route-table)]
    (fn [request]
      (try
        (when-let [route-template (find-route-template compiled request)]
          (trace-http/add-route-data! (:request-method request) route-template))
        (catch Exception e
          (log/warnf e "OTel route matching failed for %s %s"
                     (:request-method request) (:uri request))))
      (handler request))))
