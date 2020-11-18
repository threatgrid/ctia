(ns ctia.http.middleware.auth
  (:require [ctia.auth :as auth]
            [ctia.http.routes.common :as routes.common]
            [compojure.api.meta :as meta]
            [ring.util.http-response :as http-response]
            [schema.core :as s]))

(defn add-id-to-request
  "Add id metas to the request"
  [request id login groups auth-header]
  (if (some? id)
    (-> request
        (assoc :identity id
               :groups groups
               :login login)
        (assoc-in [:headers "authorization"] auth-header))
    request))

(s/defn testable-wrap-authentication
  "wrap-autentication middleware."
  [handler
   identity-for-token :- (s/=> s/Any s/Any)]
  (fn [request]
    (handler
     (if (:login request)
       request
       (let [auth-header (or (get-in request [:headers "authorization"])
                             (get-in request [:query-params "Authorization"]))
             id (identity-for-token auth-header)
             login (auth/login id)
             groups (auth/groups id)]
         (add-id-to-request request id login groups auth-header))))))

(s/defn wrap-authentication [handler identity-for-token :- (s/=> s/Any s/Any)]
  (testable-wrap-authentication handler identity-for-token))

(defn wrap-authenticated [handler]
  (fn [request]
    (let [auth-identity (:identity request)]
      (if (and (not (nil? auth-identity))
               (auth/authenticated? auth-identity))
        (handler request)
        (http-response/unauthorized!
         {:error :not_authenticated
          :message "Only authenticated users allowed"})))))

(defn require-capability! [required-capability id]
  (when required-capability
    (cond
      (or (nil? id)
          (not (auth/authenticated? id)))
      (http-response/unauthorized!
       {:error :not_authenticated
        :message "Only authenticated users allowed"})

      (not (auth/capable? id required-capability))
      (http-response/forbidden! {:message "Missing capability"
                                 :error :missing_capability
                                 :capabilities required-capability
                                 :owner (auth/login id)}))))

;; Create a compojure-api meta-data handler for capability-based
;; security. The :identity field must by on the request object
;; already, put there by the wrap-authentication middleware. This
;; lets us add :capabilities to the handler spec.
;; Reference:
;; https://github.com/metosin/compojure-api/wiki/Creating-your-own-metadata-handlers
(defmethod meta/restructure-param :capabilities [_ capabilities acc]
  (-> acc
      (update :lets into
              ['_ `(require-capability! ~capabilities
                                        (:identity ~'+compojure-api-request+))])
      (update-in [:swagger :description]
                 (fn [old]
                   (str
                    (routes.common/capabilities->description capabilities)
                    (when old
                      (str "\n\n" old)))))))

(defmethod meta/restructure-param :login [_ bind-to acc]
  (update acc :lets into
          [bind-to `(:login ~'+compojure-api-request+)]))

(defmethod meta/restructure-param :groups [_ bind-to acc]
  (update acc :lets into
          [bind-to `(:groups ~'+compojure-api-request+)]))

(defmethod meta/restructure-param :auth-identity [_ bind-to acc]
  (update acc :lets into
          [bind-to `(:identity ~'+compojure-api-request+)]))

(defmethod meta/restructure-param :identity-map [_ bind-to acc]
  (update acc :lets into
          [bind-to `(auth/ident->map
                     (:identity ~'+compojure-api-request+))]))
