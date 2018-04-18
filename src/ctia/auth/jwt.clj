(ns ctia.auth.jwt
  (:require [ctia.auth :as auth :refer [IIdentity]]
            [ctia.properties :as prop]
            [clj-momo.lib.set :refer [as-set]]
            [clojure.set :as set]))

(def casebook-capabilities
  #{:create-casebook
    :read-casebook
    :list-casebooks
    :delete-casebook
    :search-casebook})

(def entity-root-scope
  (get-in @prop/properties [:ctia :auth :entities :scope]
          "private-intel"))

(def is-global?
  (= entity-root-scope "global-intel"))

(def ^:private read-only-ctia-capabilities
  (:user auth/default-capabilities))

(def ^:private ctia-capabilities
  (set/difference auth/all-capabilities
                  (set/union
                   casebook-capabilities
                   #{:specify-id
                     :developer})))

(def claim-prefix
  (get-in @prop/properties [:ctia :http :jwt :claim-prefix]
          "https://schemas.cisco.com/iroh/identity/claims"))

(defn iroh-claim
  "JWT specific claims for iroh are URIs

  For example:

  https://schemas.cisco.com/iroh/identity/claims/user/id
  https://schemas.cisco.com/iroh/identity/claims/user/name
  https://schemas.cisco.com/iroh/identity/claims/user/email
  https://schemas.cisco.com/iroh/identity/claims/org/id
  https://schemas.cisco.com/iroh/identity/claims/roles

  See https://github.com/threatgrid/iroh/issues/1707

  Note iroh-claim are strings not keywords because from
  https://clojure.org/reference/reader
  '/' has special meaning.
  "
  [keyword-name]
  (str claim-prefix "/" keyword-name))

(defrecord JWTIdentity [jwt]
  IIdentity
  (authenticated? [_]
    true)
  (login [_]
    (:sub jwt))
  (groups [_]
    (remove nil? [(get jwt (iroh-claim "org/id"))]))
  (allowed-capabilities [_]
    (let [scopes (set (get jwt (iroh-claim "scopes")))
          ctia-caps (if (contains? scopes entity-root-scope)
                      (if is-global?
                        read-only-ctia-capabilities
                        ctia-capabilities)
                      #{})
          casebook-caps
          (if (and (not is-global?) ;; casebook should only be accessible in
                   ;; private instances
                   (contains? scopes "casebook"))
            casebook-capabilities
            #{})]
      (set/union ctia-caps casebook-caps)))
  (capable? [this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 (auth/allowed-capabilities this))))

(defn wrap-jwt-to-ctia-auth
  [handler]
  (fn [request]
    (handler
     (if-let [jwt (:jwt request)]
       (let [identity (->JWTIdentity jwt)]
         (assoc request
                :identity identity
                :login    (auth/login identity)
                :groups   (auth/groups identity)))
       request))))
