(ns ctia.encryption.default
  (:require
   [clojure.tools.logging :as log]
   [ctia.encryption :as encryption :refer [IEncryption]]
   [ctia.encryption.default-core :as core]
   [ctia.properties :as p]
   [puppetlabs.trapperkeeper.core :as tk]
   [puppetlabs.trapperkeeper.services :refer [service-context]]))


(tk/defservice default-encryption-service
  IEncryption
  []
  (init [this context]
    (log/info "Loading Encryption Key")
    (core/init context
               (get-in (p/read-global-properties) [:ctia :encryption])))
  (start [this context]
         (reset! encryption/encryption-service this)
         (core/start context))
  (stop [this context]
        (reset! encryption/encryption-service nil)
        (core/stop context))
  (decrypt [this src] (core/decrypt (service-context this) src))
  (encrypt [this src] (core/encrypt (service-context this) src)))
