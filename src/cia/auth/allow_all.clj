(ns cia.auth.allow-all
  (:require [cia.auth :refer [IIdentity IAuth]]))

(defrecord Identity []
  IIdentity
  (identity-key [_]
    [0 "admin"])
  (printable-identity [_]
    "allow-all"))

(def identity-singleton
  (->Identity))

(defrecord AuthService []
  IAuth
  (capabilities-for-token [_ _]
    :admin)
  (capabilities-for-identity [_ _]
    :admin)
  (identity-for-token [_ _]
    identity-singleton)
  (identity-has-capability? [_ _ _]
    true))
