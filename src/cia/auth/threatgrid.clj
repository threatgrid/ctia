(ns cia.auth.threatgrid
  (:require [cheshire.core :as json]
            [cia.auth :as auth]
            [cia.properties :refer [properties]]
            [cia.store :as store]
            [clj-http.client :as http]
            [clojure.core.memoize :as memo]
            [clojure.set :as set]))

(defrecord Identity [org-id role]
  auth/IIdentity
  (identity-key [_]
    [org-id role])
  (printable-identity [_]
    (str "org:" org-id ";role:" role)))

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
              :ttl/threshold (* 1000 60 5)))))

(defrecord AuthService [whoami-service]
  auth/IAuth
  (capabilities-for-token [this token]
    (->> (auth/identity-for-token this token)
         (auth/capabilities-for-identity this)))

  (capabilities-for-identity [_ id]
    (some-> (store/read-auth-role @store/auth-role-store id)
            :capabilities
            set))

  (identity-for-token [_ token]
    (if-let [{{:strs [organization_id role]} "data"} (whoami whoami-service
                                                             token)]
      (->Identity organization_id role)))

  (identity-has-capability? [this desired-capability identity]
    (let [allowed-capabilities (auth/capabilities-for-identity this identity)]
      (some?
       (first
        (set/intersection
         (cond (set? desired-capability) desired-capability
               (keyword? desired-capability) #{desired-capability}
               (sequential? desired-capability) (set desired-capability))
         allowed-capabilities))))))

(defn make-auth-service [whoami-service]
  (AuthService. whoami-service))
