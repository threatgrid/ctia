(ns ctia.auth.jwt
  (:require [ctia.auth :as auth]
            [clj-momo.lib.set :refer [as-set]]
            [clojure.set :as set]))

(defrecord Identity [jwt]
  auth/IIdentity
  (authenticated? [_]
    true)
  (login [_]
    (:sub jwt))
  (groups [_]
    (remove nil? [(:business_guid jwt)]))
  (allowed-capabilities [_]
    (:user auth/default-capabilities))
  (capable? [this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 (auth/allowed-capabilities this))))

(defn wrap-jwt-to-ctia-auth
  [handler]
  (fn [request]
    (handler
     (if-let [jwt (:jwt request)]
       (let [identity (->Identity jwt)]
         (assoc request
                :identity identity
                :login    (auth/login identity)
                :groups   (auth/groups identity)))
       request))))
