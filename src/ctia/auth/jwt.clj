(ns ctia.auth.jwt
  (:require [ctia.auth :as auth :refer [IIdentity]]
            [ctia.properties :as prop]
            [clj-momo.lib.set :refer [as-set]]
            [clojure.set :as set]))

(def ^:private write-capabilities
  (set/difference auth/all-capabilities
                  #{:specify-id
                    :developer}))

(def claim-prefix
  (get-in @prop/properties [:ctia :http :jwt :claim-prefix]
          "https://schemas.cisco.com/iroh/identity/claims"))

(defn iroh-claim
  "JWT specific claims for iroh are URIs

  For example:

  https://schemas.cisco.com/iroh/identity/claims/user/id
  https://schemas.cisco.com/iroh/identity/claims/user/name
  https://schemas.cisco.com/iroh/identity/claims/user/email
  https://schemas.cisco.com/iroh/identity/claims/org/id
  https://schemas.cisco.com/iroh/identity/claims/roles

  See https://github.com/threatgrid/iroh/issues/1707

  Note iroh-claim are strings not keywords because from
  https://clojure.org/reference/reader
  '/' has special meaning.
  "
  [keyword-name]
  (str claim-prefix "/" keyword-name))

(defrecord JWTIdentity [jwt]
  IIdentity
  (authenticated? [_]
    true)
  (login [_]
    (:sub jwt))
  (groups [_]
    (remove nil? [(get jwt (iroh-claim "org/id"))]))
  (allowed-capabilities [_]
    write-capabilities)
  (capable? [this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 (auth/allowed-capabilities this))))

(defn wrap-jwt-to-ctia-auth
  [handler]
  (fn [request]
    (handler
     (if-let [jwt (:jwt request)]
       (let [identity (->JWTIdentity jwt)]
         (assoc request
                :identity identity
                :login    (auth/login identity)
                :groups   (auth/groups identity)))
       request))))
