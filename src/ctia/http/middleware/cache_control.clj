(ns ctia.http.middleware.cache-control
  (:require [pandect.algo.sha1 :refer [sha1]])
  (:import [java.io File]))

(defn- read-request? [request]
  (#{:get :head} (:request-method request)))

(defn- ok-response? [response]
  (= (:status response) 200))

(defn calculate-etag [accept body]
  (let [etagc
        (cond
          (instance? String body) (sha1 (str accept ":" body))
          (instance? File body) (let [^File body body]
                                  (str (.lastModified body) "-" (.length body)))
          :else (sha1 (.getBytes (str accept ":" (pr-str body)) "UTF-8")))]
    (str \" etagc \")))

(defn update-headers
  [headers etag body]
  (if etag
    (assoc headers "ETag" etag)
    headers))

(defn wrap-cache-control
  "only applies to GET requests
  appends Last-Modified and Etag headers to body response"
  [handler]
  (fn [req]
    (let [{body :body
           :as resp} (handler req)
          accept (get-in req [:headers :accept])
          etag (calculate-etag accept body)]
      (if (and (read-request? req)
               (ok-response? resp))
        (update resp :headers update-headers etag body)
        resp))))
