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
  "True when the identity carries no real org/tenant: either it has no groups at
   all (a JWT missing `org/id`, or static-auth with `ctia.auth.static.group`
   unset), or it carries only the not-logged-in sentinel group (the
   `readonly-for-anonymous` ReadOnlyIdentity, which reports
   `not-logged-in-groups`). Such a caller has no tenant to scope a tenant-local
   decision (e.g. a verdict) to.

   Takes the map form (`IdentityMap`) rather than an `IIdentity` record on
   purpose: a plain `:groups` key exists on only SOME implementations. On
   `ctia.auth.threatgrid/Identity` there is a literal `groups` field, so
   `(:groups record)` happens to return the real group list and the predicate
   would answer correctly -- but on the static and JWT identities groups live
   only behind the `groups` protocol method, so `(:groups record)` is `nil` and
   the predicate would wrongly answer `true`. A record read is therefore
   silently correct on threatgrid and silently wrong elsewhere; callers must
   pass the identity map (`ident->map` / the route `identity-map`) so the answer
   is uniform across implementations.

   Blank group strings are treated as absent: a JWT with a blank `org/id` claim
   (or static-auth with a blank `ctia.auth.static.group`) reports `[\"\"]`, which
   is neither empty nor the sentinel. Dropping blanks here makes one predicate
   decide the org-less question for every implementation, so no identity slips
   through to issue a match-nothing `{:terms {\"groups\" [\"\"]}}` verdict filter."
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
