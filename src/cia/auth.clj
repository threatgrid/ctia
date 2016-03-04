(ns cia.auth
  (:require [cheshire.core :as json]
            [cia.store :as store]
            [clojure.core.memoize :as memo]
            [clj-http.client :as http]
            [clojure.set :as set]))

(defprotocol IIdentity
  (identity-key [this])
  (printable-identity [this]))

(defprotocol IAuth
  (capabilities-for-token [this token])
  (capabilities-for-identity [this identity])
  (identity-for-token [this token])
  (identity-has-capability? [this desired-capability identity]))

(defprotocol IWhoAmI
  (whoami [this token]))

(defn make-whoami-fn [whoami-url]
  (fn [token]
    (let [response (http/get whoami-url {:accept :json
                                         :throw-exceptions false
                                         :query-params {"api_key" token}})]
      (if (= 200 (:status response))
        (-> (:body response)
            json/parse-string)))))

(defrecord WhoamiService [url whoami-fn]
  IWhoAmI
  (whoami [_ token]
    (whoami-fn token)))

(defn make-whoami-service
  ([] (make-whoami-service "https://panacea.threatgrid.com/api/v3/session/whoami"))
  ([url]
   (WhoamiService.
    url
    (memo/ttl (make-whoami-fn url)
              :ttl/threshold (* 1000 60 5)))))

(defrecord Identity [org-id role]
  IIdentity
  (identity-key [_]
    [org-id role])
  (printable-identity [_]
    (str "org:" org-id ";role:" role)))

(defrecord AuthService [whoami-service]
  IAuth
  (capabilities-for-token [this token]
    (->> (identity-for-token this token)
         (capabilities-for-identity this)))

  (capabilities-for-identity [_ id]
    (some-> (store/read-auth-role @store/auth-role-store id)
            :capabilities
            set))

  (identity-for-token [_ token]
    (if-let [{{:strs [organization_id role]} "data"} (whoami whoami-service
                                                             token)]
      (->Identity organization_id role)))

  (identity-has-capability? [this desired-capability identity]
    (let [allowed-capabilities (capabilities-for-identity this identity)]
      (some?
       (first
        (set/intersection
         (cond (set? desired-capability) desired-capability
               (keyword? desired-capability) #{desired-capability}
               (sequential? desired-capability) (set desired-capability))
         allowed-capabilities))))))

(defn make-auth-service [whoami-service]
  (AuthService. whoami-service))

;; TODO - get whoami URL from a properties file
(defonce auth-service (atom (make-auth-service (make-whoami-service))))

(defn set-whoami-service! [whoami-service]
  (swap! auth-service assoc :whoami-service whoami-service))
