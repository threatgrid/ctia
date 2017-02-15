(ns ctia.http.middleware.cache-control
  (:require [clj-momo.lib.time :refer [format-rfc822-time]]
            [pandect.algo.sha1 :refer [sha1]])
  (:import java.io.File))

(defn- read-request? [request]
  (#{:get :head} (:request-method request)))

(defn- ok-response? [response]
  (= (:status response) 200))

(defn calculate-etag [body]
  (case (class body)
    String (sha1 body)
    File (str (.lastModified body) "-" (.length body))
    (sha1 (.getBytes (pr-str body) "UTF-8"))))

(defn update-headers
  [headers etag {:keys [created updated] :as body}]
  (let [last-modified (or updated created)]
    (cond-> headers
      etag (assoc "ETag" etag)
      last-modified (assoc "Last-Modified"
                           (format-rfc822-time last-modified)))))

(defn wrap-cache-control
  "only applies to GET requests
  appends Last-Modified and Etag headers to body response"

  [handler]
  (fn [req]
    (let [{body :body :as resp} (handler req)
          etag (calculate-etag body)]

      (if (and (read-request? req)
               (ok-response? resp))
        (update resp :headers #(update-headers % etag body))
        resp))))

(def ^:deprecated wrap-cache-control-headers wrap-cache-control)
