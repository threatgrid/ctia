(ns ctia.auth
  (:require [schema.core :as s]))

(defprotocol IIdentity
  (authenticated? [this])
  (login [this])
  (groups [this])
  (allowed-capabilities [this])
  (capable? [this capabilities]))

(defprotocol IAuth
  (identity-for-token [this token]))

(defonce auth-service (atom nil))

(def not-logged-in-owner "Unknown")

(def not-logged-in-groups ["Unknown Group"])

(def admingroup "Administrators")

(defrecord DeniedIdentity []
  IIdentity
  (authenticated? [_]
    false)
  (login [_]
    not-logged-in-owner)
  (groups [_]
    not-logged-in-groups)
  (allowed-capabilities [_]
    #{})
  (capable? [_ _]
    false))

(def denied-identity-singleton (->DeniedIdentity))

(s/defn ident->map :- (s/maybe {:login (s/maybe s/Str)
                                :groups (s/maybe [s/Str])})
  [ident]
  (when ident
    {:login (login ident)
     :groups (groups ident)}))
