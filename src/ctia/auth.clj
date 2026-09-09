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

(s/defschema AuthIdentity
  (s/protocol IIdentity))

(s/defschema IdentityMap
  {:client-id (s/maybe s/Str)
   :login (s/maybe s/Str)
   :groups [s/Str]})

(s/defn orgless-ident? :- s/Bool
  "True when the identity carries no real org/tenant: either it has no groups at
   all (a JWT missing `org/id`, or static-auth with `ctia.auth.static.group`
   unset), or it carries only the not-logged-in sentinel group (the
   `readonly-for-anonymous` ReadOnlyIdentity, which reports
   `not-logged-in-groups`). Such a caller has no tenant to scope a tenant-local
   decision (e.g. a verdict) to.

   Takes the map form (`IdentityMap`) rather than an `IIdentity` record: groups
   live behind the `groups` protocol method on a record, not a `:groups` key, so
   `(:groups record)` would be `nil` and the predicate would wrongly answer
   `true`. Callers must pass the identity map (`ident->map` / the route
   `identity-map`)."
  [ident :- IdentityMap]
  (let [groups (:groups ident)]
    (boolean (or (empty? groups)
                 (= (set not-logged-in-groups) (set groups))))))

(s/defn ident->map :- (s/maybe IdentityMap)
  [ident :- (s/maybe AuthIdentity)]
  (when ident
    {:login (login ident)
     :groups (groups ident)
     :client-id (client-id ident)}))
