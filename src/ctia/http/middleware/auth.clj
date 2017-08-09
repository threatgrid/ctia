(ns ctia.http.middleware.auth
  (:require [ctia.auth :as auth :refer [auth-service]]
            [compojure.api.meta :as meta]
            [ring.util.http-response :as http-response]))

(defn wrap-authentication [handler]
  (fn [request]
    (let [Authorization (or (get-in request [:headers "authorization"])
                      (get-in request [:query-params "Authorization"]))
          id (auth/identity-for-token @auth-service Authorization)]
      (handler
       (-> request
           (assoc :identity id
                  :login (auth/login id))
           (assoc-in [:headers "authorization"] Authorization))))))

(defn require-capability! [required-capability id]
  (if (and required-capability
           (auth/require-login? @auth/auth-service))
    (cond
      (not (auth/authenticated? id))
      (http-response/forbidden! {:message "Only authenticated users allowed"})

      (not (auth/capable? id required-capability))
      (http-response/unauthorized! {:message "Missing capability"
                                    :capabilities required-capability
                                    :owner (auth/login id)}))))


;; Create a compojure-api meta-data handler for capability-based
;; security. The :identity field must by on the request object
;; already, put there by the wrap-authentication middleware. This
;; lets us add :capabilities to the handler spec.
;; Reference:
;; https://github.com/metosin/compojure-api/wiki/Creating-your-own-metadata-handlers
(defmethod meta/restructure-param :capabilities [_ capabilities acc]
  (update acc :lets into
          ['_ `(require-capability! ~capabilities
                                    (:identity ~'+compojure-api-request+))]))

(defmethod meta/restructure-param :login [_ bind-to acc]
  (update acc :lets into
          [bind-to `(:login ~'+compojure-api-request+)]))

(defmethod meta/restructure-param :identity [_ bind-to acc]
  (update acc :lets into
          [bind-to `(:identity ~'+compojure-api-request+)]))
