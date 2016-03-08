(ns cia.auth.middleware
  (:require [cia.auth :as auth :refer [auth-service]]
            [compojure.api.meta :as meta]
            [ring.util.http-response :as http-response]))

(defn wrap-authentication [handler]
  (fn [request]
    (let [api_key (or (get-in request [:headers "api_key"])
                      (get-in request [:query-params "api_key"]))
          id (if api_key (auth/identity-for-token @auth-service api_key))]
      (handler
       (-> request
           (assoc :identity id
                  :login (if id (auth/login id)))
           (assoc-in [:headers "api_key"] api_key))))))

(defn require-capability! [granting-capabilities id]
  (if granting-capabilities
    (cond
      (nil? id)
      (http-response/forbidden! {:message "Only authenticated users allowed"})

      (not (auth/allowed-capability? id granting-capabilities))
      (http-response/unauthorized! {:message "Missing capability"
                                    :capabilities granting-capabilities
                                    :owner (auth/login id)}))))


;; Create a compojure-api meta-data handler for capability-based
;; security. The :owner field must by on the request object
;; already, put there by buddy-auth.  This lets us add :capabilities
;; to the handler spec.
;; Reference:
;; https://github.com/metosin/compojure-api/wiki/Creating-your-own-metadata-handlers
(defmethod meta/restructure-param :capabilities [_ capabilities acc]
  (update acc :lets into
          ['_ `(require-capability! ~capabilities
                                    (:identity ~'+compojure-api-request+))]))

(defmethod meta/restructure-param :login [_ bind-to acc]
  (update acc :lets into
          [bind-to `(:login ~'+compojure-api-request+)]))
