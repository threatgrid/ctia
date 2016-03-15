(ns cia.auth.allow-all
  (:require [cia.auth
             :refer [IIdentity IAuth]
             :as auth]))

(defrecord Identity []
  IIdentity
  (authenticated? [_]
    false)
  (login [_]
    auth/not-logged-in-owner)
  (allowed-capabilities [_]
    (get auth/default-capabilities :admin))
  (allowed-capability? [_ _]
    true))

(def identity-singleton
  (->Identity))

(defrecord AuthService []
  IAuth
  (identity-for-token [_ _]
    identity-singleton)
  (require-login? [_]
    false))
