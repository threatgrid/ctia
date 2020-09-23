(ns ctia.encryption)

(defonce encryption-service (atom nil))

(defprotocol IEncryption
  (encrypt [this s])
  (decrypt [this s]))
