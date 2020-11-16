(ns ctia.auth.allow-all
  (:require [clj-momo.lib.set :refer [as-set]]
            [ctia.auth.capabilities :refer [all-capabilities]]
            [ctia.auth
             :refer [IIdentity IAuth]
             :as auth]
            [puppetlabs.trapperkeeper.core :as tk]))

(defrecord Identity []
  IIdentity
  (authenticated? [_]
    true)
  (login [_]
    auth/not-logged-in-owner)
  (groups [_]
    [auth/admingroup])
  (allowed-capabilities [_]
    (all-capabilities))
  (capable? [_ _]
    true)
  (rate-limit-fn [_ _]))

(def identity-singleton
  (->Identity))

(tk/defservice allow-all-auth-service
  IAuth
  []
  (identity-for-token [_ _]
    identity-singleton))
