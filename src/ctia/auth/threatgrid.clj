(ns ctia.auth.threatgrid
  (:require
   [ctia.auth.capabilities
    :refer [default-capabilities]]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clj-momo.lib.set :refer [as-set]]
   [clojure
    [set :as set]
    [string :as str]]
   [clojure.core.memoize :as memo]
   [ctia
    [auth :as auth]
    [properties :refer [properties]]
    [store :as store]]))

(def cache-ttl-ms (* 1000 60 5))

(defn- memo [f]
  (memo/ttl f :ttl/threshold cache-ttl-ms))

(defrecord Identity
    [role
     login
     groups
     capabilities]
  auth/IIdentity
  (authenticated? [_]
    true)
  (login [_]
    login)
  (groups [_]
    (remove nil? groups))
  (allowed-capabilities [_]
    capabilities)
  (capable? [this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 (auth/allowed-capabilities this))))

(defn make-whoami-fn [whoami-url]
  (fn [token]
    (let [response (http/get whoami-url
                             {:accept :json
                              :throw-exceptions false
                              :query-params {"api_key" token}})]
      (if (= 200 (:status response))
        (json/parse-string
         (:body response))))))

(defn lookup-stored-identity [login]
  (store/read-store :identity store/read-identity login))

(defrecord ThreatgridAuthService [whoami-fn
                                  lookup-stored-identity-fn]
  auth/IAuth
  (identity-for-token [_ token]
    (or (when-let [{{:strs [role
                            login
                            organization_id]} "data"}
                   (when token (whoami-fn token))]
          (when (and role login organization_id)
            (map->Identity (or (lookup-stored-identity-fn login)
                               {:login login
                                ;; TODO check if this the right field we could use here
                                :groups [organization_id]
                                :role role
                                :capabilities (->> (str/lower-case role)
                                                   keyword
                                                   (get default-capabilities))}))))
        auth/denied-identity-singleton)))

(defn make-auth-service []
  (let [{:keys [whoami-url cache]} (get-in @properties [:ctia :auth :threatgrid])
        whoami-fn (make-whoami-fn whoami-url)]
    (->ThreatgridAuthService
     (if cache (memo whoami-fn) whoami-fn)
     (if cache (memo lookup-stored-identity) lookup-stored-identity))))
