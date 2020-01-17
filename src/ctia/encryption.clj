(ns ctia.encryption)

(defonce encryption-service (atom nil))

(defprotocol IEncryption
  (init [this props])
  (encrypt [this s])
  (decrypt [this s]))

(defn encrypt-str [s]
  (encrypt @encryption-service s))

(defn decrypt-str [s]
  (decrypt @encryption-service s))
