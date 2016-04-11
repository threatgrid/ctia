(ns ctia.stores.atom.ttp
  (:require [ctia.schemas.ttp :refer [NewTTP StoredTTP realize-ttp]]
            [ctia.stores.atom.common :as mc]))

(def swap-ttp (mc/make-swap-fn realize-ttp))

(mc/def-create-handler-from-realized handle-create-ttp StoredTTP)

(mc/def-read-handler handle-read-ttp StoredTTP)

(mc/def-delete-handler handle-delete-ttp StoredTTP)

(mc/def-update-handler-from-realized handle-update-ttp StoredTTP)
