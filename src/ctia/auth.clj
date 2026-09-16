(ns ctia.auth
  (:require [clojure.string :as str]
            [schema.core :as s]))

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
  "True when the identity has no org to scope a verdict to: no groups (a JWT
   missing `org/id`, static-auth with a blank `ctia.auth.static.group`), only
   blank group strings, or only the not-logged-in sentinel. Takes the ident
   *map* (`ident->map`), not an `IIdentity` record, since `groups` is a protocol
   method rather than a field on most identities.
   See verdict scoping in resources/ctia/public/doc/design.md."
  [ident :- IdentityMap]
  (let [groups (remove str/blank? (:groups ident))]
    (boolean (or (empty? groups)
                 (= (set not-logged-in-groups) (set groups))))))

(s/defn ident->map :- (s/maybe IdentityMap)
  [ident :- (s/maybe AuthIdentity)]
  (when ident
    {:login (login ident)
     :groups (groups ident)
     :client-id (client-id ident)}))
