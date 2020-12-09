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
   [ctia.store-service.schemas :refer [ReadStoreFn]]
   [ctia
    [auth :as auth]
    [properties :as p]
    [store :as store]]
   [puppetlabs.trapperkeeper.core :as tk]
   [puppetlabs.trapperkeeper.services :refer [service-context]]
   [schema.core :as s]))

(declare make-auth-service)

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
                 (auth/allowed-capabilities this)))
  (rate-limit-fn [_ _]))

(defn make-whoami-fn [whoami-url]
  (fn [token]
    (let [response (http/get whoami-url
                             {:accept :json
                              :throw-exceptions false
                              :query-params {"api_key" token}})]
      (if (= 200 (:status response))
        (json/parse-string
         (:body response))))))

(s/defn lookup-stored-identity
  [login
   read-store :- ReadStoreFn]
  (-> (read-store :identity)
      (store/read-identity login))) 

(defprotocol ThreatgridAuthWhoAmIURLService
  (get-whoami-url [this] "Return the current WhoAmI URL"))

(tk/defservice threatgrid-auth-whoami-url-service
  ThreatgridAuthWhoAmIURLService
  [[:ConfigService get-in-config]]
  (get-whoami-url [_] (get-in-config [:ctia :auth :threatgrid :whoami-url])))

(tk/defservice threatgrid-auth-service
  auth/IAuth
  [[:ConfigService get-in-config]
   [:StoreService read-store]
   [:ThreatgridAuthWhoAmIURLService get-whoami-url]]
  (init [_ context]
        (let [lookup-stored-identity #(lookup-stored-identity % read-store)]
          (into context
                (make-auth-service get-in-config lookup-stored-identity get-whoami-url))))

  (identity-for-token [this token]
   (let [{:keys [whoami-fn lookup-stored-identity-fn]} (service-context this)]
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
        auth/denied-identity-singleton))))

(defn make-auth-service [get-in-config lookup-stored-identity get-whoami-url]
  (let [{:keys [cache]} (get-in-config [:ctia :auth :threatgrid])
        whoami-fn (make-whoami-fn (get-whoami-url))]
    {:whoami-fn
     (if cache (memo whoami-fn) whoami-fn)
     :lookup-stored-identity-fn
     (if cache (memo lookup-stored-identity) lookup-stored-identity)}))
