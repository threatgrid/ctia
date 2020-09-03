(ns ctia.encryption.default
  (:require
   [clojure.tools.logging :as log]
   [clojure.string :as string]
   [ctia.encryption :refer [IEncryption]]
   [lock-key.core :refer [decrypt-from-base64
                          encrypt-as-base64]]
   [ctia.tk :as tk]
   [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defn encryption-key
  [{:keys [secret key]}]
  (assert (or secret (:filepath key))
          "no secret or key filepath provided")
  (or secret
      (some-> (:filepath key)
              slurp
              string/trim)))

(tk/defservice default-encryption-service
  IEncryption
  [[:ConfigService get-in-config]]
  (init [this context]
    (log/info "Loading Encryption Key")
    (let [secret (encryption-key (get-in-config [:ctia :encryption]))]
      (into context
              {:encrypt-fn #(encrypt-as-base64 % secret)
               :decrypt-fn #(decrypt-from-base64 % secret)})))
  (decrypt [this src]
    (let [{:keys [decrypt-fn]} (service-context this)]
      (decrypt-fn src)))
  (encrypt [this src]
    (let [{:keys [encrypt-fn]} (service-context this)]
      (encrypt-fn src))))
