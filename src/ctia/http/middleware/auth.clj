(ns ctia.http.middleware.auth
  (:require [ctia.auth :as auth :refer [auth-service]]
            [compojure.api.meta :as meta]
            [ring.util.http-response :as http-response]))

(defn add-id-to-request
  "Given a request and a discoverd id and auth header
  change the request accordingly"
  [request id login auth-header]
  (-> request
      (assoc :identity id
             :login login)
      (assoc-in [:headers "authorization"] auth-header)))

(defn testable-wrap-authentication
  "wrap-autentication middleware."
  [handler auth-service]
  (fn [request]
    (handler
     (if (:login request)
       request
       (let [auth-header (or (get-in request [:headers "authorization"])
                             (get-in request [:query-params "Authorization"]))
             id (auth/identity-for-token auth-service auth-header)
             login (auth/login id)]
         (cond-> request
           (some? id) (add-id-to-request id login auth-header)))))))

(defn wrap-authentication [handler]
  (testable-wrap-authentication handler @auth-service))

(defn require-capability! [required-capability id]
  (if required-capability
    (cond
      (or (nil? id)
          (not (auth/authenticated? id)))
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
