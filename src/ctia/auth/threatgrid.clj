(ns ctia.auth.threatgrid
  (:require [cheshire.core :as json]
            [ctia.auth :as auth]
            [ctia.properties :refer [properties]]
            [ctia.schemas.identity :as id]
            [ctia.store :as store]
            [clj-http.client :as http]
            [clojure.core.memoize :as memo]
            [clojure.set :as set]
            [clojure.string :as str]
            [schema.core :as s]))

(def cache-ttl-ms (* 1000 60 5))

(defn- memo [f]
  (memo/ttl f :ttl/threshold cache-ttl-ms))

(defn- as-set [x]
  (cond (set? x) x
        (keyword? x) #{x}
        (sequential? x) (set x)))

(defrecord Identity [role login capabilities]
  auth/IIdentity
  (authenticated? [_]
    true)
  (login [_]
    login)
  (allowed-capabilities [_]
    capabilities)
  (allowed-capability? [this desired-capability]
    (some?
     (first
      (set/intersection
       (as-set desired-capability)
       (auth/allowed-capabilities this))))))

(defn make-whoami-fn [whoami-url]
  (fn [token]
    (let [response (http/get whoami-url
                             {:accept :json
                              :throw-exceptions false
                              :query-params {"api_key" token}})]
      (if (= 200 (:status response))
        (-> (:body response)
            json/parse-string)))))

(defn lookup-stored-identity [login]
  (store/read-identity @store/identity-store login))

(defrecord ThreatgridAuthService [whoami-fn lookup-stored-identity-fn]
  auth/IAuth
  (identity-for-token [_ token]
    (or
     (when-let [{{:strs [role login]} "data"} (when token (whoami-fn token))]
       (when (and role login)
         (map->Identity (or (lookup-stored-identity-fn login)
                            {:login login
                             :role role
                             :capabilities (->> (str/lower-case role)
                                                keyword
                                                (get auth/default-capabilities))}))))
     auth/denied-identity-singleton))
  (require-login? [_]
    true))

(defn make-auth-service []
  (let [{:keys [whoami-url cache]} (get-in @properties [:ctia :auth :threatgrid])
        whoami-fn (make-whoami-fn whoami-url)]
    (->ThreatgridAuthService
     (if cache (memo whoami-fn) whoami-fn)
     (if cache (memo lookup-stored-identity) lookup-stored-identity))))
