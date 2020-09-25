(ns ctia.encryption)

(defprotocol IEncryption
  (encrypt [this s])
  (decrypt [this s]))
