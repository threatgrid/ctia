(ns ctia.encryption.default
  (:require
   [clojure.tools.logging :as log]
   [clojure.string :as string]
   [ctia.encryption :refer [IEncryption]]
   [lock-key.core :refer [decrypt-from-base64
                          encrypt-as-base64]]))

(defn encryption-key
  [{:keys [secret key]}]
  (assert (or secret (:filepath key))
          "no secret or key filepath provided")
  (or secret
      (some-> (:filepath key)
              slurp
              string/trim)))

(defrecord EncryptionService [state]
  IEncryption
  (init [this props]
    (log/info "Loading Encryption Key")
    (let [secret (encryption-key props)]
      (reset! (:state this)
              {:encrypt-fn #(encrypt-as-base64 % secret)
               :decrypt-fn #(decrypt-from-base64 % secret)})))
  (decrypt [{:keys [state]} src]
    (let [{:keys [decrypt-fn]} @state]
      (decrypt-fn src)))
  (encrypt [{:keys [state]} src]
    (let [{:keys [encrypt-fn]} @state]
      (encrypt-fn src))))
