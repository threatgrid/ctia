(ns ctia.stores.atom.ttp
  (:require [ctia.schemas.ttp :refer [NewTTP StoredTTP realize-ttp]]
            [ctia.stores.atom.common :as mc]))

(def swap-ttp (mc/make-swap-fn realize-ttp))

(mc/def-create-handler handle-create-ttp
  StoredTTP NewTTP swap-ttp (mc/random-id "ttp"))

(mc/def-read-handler handle-read-ttp StoredTTP)

(mc/def-delete-handler handle-delete-ttp StoredTTP)

(mc/def-update-handler handle-update-ttp
  StoredTTP NewTTP swap-ttp)
