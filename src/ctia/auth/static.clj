(ns ctia.auth.static
  (:require [clj-momo.lib.set :refer [as-set]]
            [clojure
             [set :as set]
             [string :as str]]
            [ctia.auth :as auth :refer [IAuth IIdentity]]
            [ctia.auth.capabilities :refer [all-capabilities]]))

(def ^:private write-capabilities
  (set/difference all-capabilities
                  #{:specify-id}))

(def ^:private read-only-capabilities
  (set/difference (->> all-capabilities
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
  (login [_]
    name)
  (groups [_]
    (remove nil? [guid]))
  (allowed-capabilities [_]
    write-capabilities)
  (capable? [this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 write-capabilities)))

(defrecord ReadOnlyIdentity []
  IIdentity
  (authenticated? [_]
    true)
  (login [_]
    auth/not-logged-in-owner)
  (groups [_]
    (remove nil? auth/not-logged-in-groups))
  (allowed-capabilities [_]
    read-only-capabilities)
  (capable? [this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 read-only-capabilities)))

(defrecord AuthService [auth-config]
  IAuth
  (identity-for-token [_ token]
    (let [secret (get-in auth-config [:static :secret])
          readonly? (get-in auth-config [:static :readonly-for-anonymous])]
      (cond
        (= token secret) (->WriteIdentity (get-in auth-config [:static :name])
                                          (get-in auth-config [:static :group]))
        ;; Readonly access when the password does not match
        readonly? (->ReadOnlyIdentity)
        :else auth/denied-identity-singleton))))
