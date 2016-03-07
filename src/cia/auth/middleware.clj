(ns cia.auth.middleware
  (:require [cia.auth :as auth :refer [auth-service]]
            [compojure.api.meta :as meta]
            [ring.util.http-response :as http-response]))

(defn wrap-authentication [handler]
  (fn [request]
    (handler
     (assoc request
            :identity (some->> (get-in request [:headers "api_key"])
                               (auth/identity-for-token @auth-service))))))

(defn require-capability! [required-capabilities id]
  (if required-capabilities
    (cond
      (nil? id)
      (http-response/forbidden! {:message "Only authenticated users allowed"})

      (not (auth/allowed-capability? id required-capabilities))
      (http-response/unauthorized! {:message "Missing capability"
                                    :required required-capabilities
                                    :owner (auth/login id)}))))


;; Create a compojure-api meta-data handler for capability-based
;; security. The :owner field must by on the request object
;; already, put there by buddy-auth.  This lets us add :capabilities
;; to the handler spec.
;; Reference:
;; https://github.com/metosin/compojure-api/wiki/Creating-your-own-metadata-handlers
(defmethod meta/restructure-param :capabilities
  [_ capabilities acc]
  (update-in acc
             [:lets]
             into
             ['_ `(require-capability! ~capabilities
                                       (:identity ~'+compojure-api-request+))]))

(defmethod meta/restructure-param :login [_ bind-to acc]
  (update-in acc [:lets] into [bind-to `(get-in  ~'+compojure-api-request+ [:identity :login])]))
