(ns ctia.auth.static
  (:require [clj-momo.lib.set :refer [as-set]]
            [clojure.set :as set]
            [ctia
             [auth :refer [IIdentity IAuth] :as auth]
             [properties :as p]]))

(def ^:private my-capabilities
  (set/difference auth/all-capabilities
                  #{:specify-id}))

(defrecord Identity [name]
  IIdentity
  (authenticated? [_]
    true)
  (login [_]
    name)
  (allowed-capabilities [_]
    my-capabilities)
  (capable? [this required-capabilities]
    (set/subset? (as-set required-capabilities)
                 my-capabilities)))

(defrecord AuthService [auth-config]
  IAuth
  (identity-for-token [_ token]
    (if (= token (get-in auth-config [:static :secret]))
      (->Identity (get-in auth-config [:static :name]))
      auth/denied-identity-singleton))
  (require-login? [_]
    true))
