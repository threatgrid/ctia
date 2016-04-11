(ns ctia.stores.atom.ttp
  (:require [ctia.schemas.ttp :refer [NewTTP StoredTTP realize-ttp]]
            [ctia.stores.atom.common :as mc]))

(def swap-ttp (mc/make-swap-fn realize-ttp))

(def handle-create-ttp (mc/create-handler-from-realized StoredTTP))

(mc/def-read-handler handle-read-ttp StoredTTP)

(mc/def-delete-handler handle-delete-ttp StoredTTP)

(def handle-update-ttp (mc/update-handler-from-realized StoredTTP))
