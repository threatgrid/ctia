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
    all-capabilities)
  (capable? [_ _]
    true)
  (rate-limit-fn [_ _]))

(def identity-singleton
  (->Identity))

(tk/defservice allow-all-auth-service
  IAuth
  []
  (start [this context]
         (reset! auth/auth-service this)
         context)
  (stop [this context]
        (reset! auth/auth-service nil)
        context)
  (identity-for-token [_ _]
    identity-singleton))

(defn allow-all-auth-service+deps []
  [allow-all-auth-service])
