(ns cia.auth)

(defprotocol IIdentity
  (identity-key [this])
  (printable-identity [this]))

(defprotocol IAuth
  (capabilities-for-token [this token])
  (capabilities-for-identity [this identity])
  (identity-for-token [this token])
  (identity-has-capability? [this desired-capability identity]))

(defonce auth-service (atom nil))
