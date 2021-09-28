(ns ctia.auth
  (:require [schema.core :as s]))

(defprotocol IIdentity
  (authenticated? [this])
  (client-id [this])
  (login [this])
  (groups [this])
  (allowed-capabilities [this])
  (capable? [this capabilities])
  (rate-limit-fn [this limit-fn]))

(defprotocol IAuth
  (identity-for-token [this token]))

(def not-logged-client-id nil)

(def not-logged-in-owner "Unknown")

(def not-logged-in-groups ["Unknown Group"])

(def admingroup "Administrators")

(defrecord DeniedIdentity []
  IIdentity
  (authenticated? [_]
    false)
  (client-id [_]
    not-logged-client-id)
  (login [_]
    not-logged-in-owner)
  (groups [_]
    not-logged-in-groups)
  (allowed-capabilities [_]
    #{})
  (capable? [_ _]
    false)
  (rate-limit-fn [_ _]))

(def denied-identity-singleton (->DeniedIdentity))

(s/defschema IdentityMap
  {:client-id (s/maybe s/Str)
   :login (s/maybe s/Str)
   :groups (s/maybe [s/Str])})

(s/defn ident->map :- (s/maybe IdentityMap)
  [ident]
  (when ident
    {:login (login ident)
     :groups (groups ident)
     :client-id (client-id ident)}))
