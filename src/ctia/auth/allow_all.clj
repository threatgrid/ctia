(ns ctia.auth.allow-all
  (:require [clj-momo.lib.set :refer [as-set]]
            [ctia.auth
             :refer [IIdentity IAuth]
             :as auth]))

(defrecord Identity []
  IIdentity
  (authenticated? [_]
    true)
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
    identity-singleton))
