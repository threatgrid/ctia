(ns ctia.http.middleware.otel
  "Ring middleware that sets the OTel http.route span attribute by
  matching the request URI against a pre-compiled route table.
  Works for all responses including those short-circuited by auth
  middleware (401s), unlike compojure.core/wrap-routes which only
  fires at leaf route handlers."
  (:require [clojure.tools.logging :as log]
            [clout.core :as clout]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]))

(defn- compile-route-table
  "Compile a route table (from compojure-api's get-routes) into a map
  of HTTP verb to vector of [compiled-path template] tuples."
  [route-table]
  (->> route-table
       (map (fn [[path method]]
              [(name method) [(clout/route-compile path) path]]))
       (reduce (fn [m [verb entry]]
                 (update m verb (fnil conj []) entry))
               {})))

(defn wrap-otel-route
  "Wraps a handler to set the OTel http.route span attribute.
  `route-table` is the result of compojure.api.routes/get-routes."
  [handler route-table]
  (let [compiled (compile-route-table route-table)]
    (fn [request]
      (try
        (when-let [method (:request-method request)]
          (let [verb (name method)]
            (when-let [route-template
                       (some (fn [[compiled-path template]]
                               (when (clout/route-matches compiled-path request)
                                 template))
                             (get compiled verb))]
              (trace-http/add-route-data! method route-template))))
        (catch Exception e
          (log/warnf e "OTel route matching failed for %s %s"
                     (:request-method request) (:uri request))))
      (handler request))))
