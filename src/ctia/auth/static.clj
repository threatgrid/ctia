(ns ctia.auth.static
  (:require [clj-momo.lib.set :refer [as-set]]
            [clojure
             [set :as set]
             [string :as str]]
            [clojure.tools.logging :as log]
            [ctia.auth :as auth :refer [IAuth IIdentity]]
            [ctia.auth.capabilities :refer [all-capabilities]]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defn ^:private write-capabilities []
  (set/difference (all-capabilities)
                  #{:specify-id}))

(defn ^:private read-only-capabilities []
  (set/difference (->> (all-capabilities)
                       (remove (fn [cap]
                                 (some #(str/starts-with? (name cap) %)
                                       ["create" "delete"])))
                       set)
                  #{:developer
                    :specify-id
                    :import-bundle}))

(defrecord WriteIdentity [name guid]
  IIdentity
  (authenticated? [_]
    true)
  (client-id [_]
    auth/not-logged-client-id)
  (login [_]
    name)
  (groups [_]
    (remove nil? [guid]))
  (allowed-capabilities [_]
    (write-capabilities))
  (capable? [_this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 (write-capabilities)))
  (rate-limit-fn [_ _]))

(defrecord ReadOnlyIdentity []
  IIdentity
  (authenticated? [_]
    true)
  (client-id [_]
    auth/not-logged-client-id)
  (login [_]
    auth/not-logged-in-owner)
  (groups [_]
    (remove nil? auth/not-logged-in-groups))
  (allowed-capabilities [_]
    (read-only-capabilities))
  (capable? [_this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 (read-only-capabilities)))
  (rate-limit-fn [_ _]))

(tk/defservice static-auth-service
  IAuth
  [[:ConfigService get-in-config]]
  (init [this context]
        (let [auth-config (get-in-config [:ctia :auth])
              static-cfg (:static auth-config)]
          ;; XFV-20: a verdict is tenant-local, so an org-less caller receives
          ;; none (HTTP 404). Warn once at boot for the two static-auth settings
          ;; that leave callers org-less -- the per-request signal is only a
          ;; `log/debugf`, off at the shipped `info` level.
          (when (str/blank? (:group static-cfg))
            (log/warn (str "ctia.auth.static.group is blank: the authenticated "
                           "static (write) identity has no org, so its verdict "
                           "requests return 404.")))
          (when (:readonly-for-anonymous static-cfg)
            (log/warn (str "ctia.auth.static.readonly-for-anonymous is on: "
                           "anonymous callers have no org, so their verdict "
                           "requests return 404.")))
          (assoc context :auth-config auth-config)))
  (identity-for-token [this token]
    (let [{:keys [auth-config]} (service-context this)
          secret (get-in auth-config [:static :secret])
          readonly? (get-in auth-config [:static :readonly-for-anonymous])]
      (cond
        (= token secret) (->WriteIdentity (get-in auth-config [:static :name])
                                          (get-in auth-config [:static :group]))
        ;; Readonly access when the password does not match
        readonly? (->ReadOnlyIdentity)
        :else auth/denied-identity-singleton))))
