(ns cia.auth.threatgrid
  (:require [cheshire.core :as json]
            [cia.auth :as auth]
            [cia.properties :refer [properties]]
            [cia.store :as store]
            [clj-http.client :as http]
            [clojure.core.memoize :as memo]
            [clojure.set :as set]
            [clojure.string :as str]))

(def cache-ttl-ms (* 1000 60 5))

(defrecord Identity [role login capabilities]
  auth/IIdentity
  (login [_]
    login)
  (allowed-capabilities [_]
    capabilities)
  (allowed-capability? [this desired-capability]
    (some?
     (first
      (set/intersection
       (cond (set? desired-capability) desired-capability
             (keyword? desired-capability) #{desired-capability}
             (sequential? desired-capability) (set desired-capability))
       (auth/allowed-capabilities this))))))

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
  ([] (make-whoami-service (get-in properties [:auth :service :threatgrid :url])))
  ([url]
   (WhoamiService.
    url
    (memo/ttl (make-whoami-fn url)
              :ttl/threshold cache-ttl-ms))))

(defn lookup-stored-identity [login]
  (store/read-identity @store/identity-store login))

(defrecord AuthService [whoami-service lookup-stored-identity-fn]
  auth/IAuth
  (identity-for-token [_ token]
    (if-let [{{:strs [role login]} "data"}
             (whoami whoami-service token)]
      (map->Identity (or (lookup-stored-identity-fn login)
                         {:login login
                          :role role
                          :capabilities (->> (str/lower-case role)
                                             keyword
                                             (get auth/default-capabilities))})))))

(defn make-auth-service
  ([whoami-service]
   (make-auth-service whoami-service
                      (memo/ttl lookup-stored-identity
                                :ttl/threshold cache-ttl-ms)))
  ([whoami-service lookup-stored-identity-fn]
   (AuthService. whoami-service
                 lookup-stored-identity-fn)))
