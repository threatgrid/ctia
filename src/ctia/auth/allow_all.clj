(ns ctia.auth.allow-all
  (:require [ctia.auth
             :refer [IIdentity IAuth]
             :as auth]
            [ctia.lib.set :refer [as-set]]))

(defrecord Identity []
  IIdentity
  (authenticated? [_]
    false)
  (login [_]
    auth/not-logged-in-owner)
  (allowed-capabilities [_]
    auth/all-capabilities)
  (capable? [_ _]
    true))

(def identity-singleton
  (->Identity))

(defrecord AuthService []
  IAuth
  (identity-for-token [_ _]
    identity-singleton)
  (require-login? [_]
    false))
