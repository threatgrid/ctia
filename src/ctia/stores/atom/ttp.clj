(ns ctia.stores.atom.ttp
  (:require [ctia.schemas.ttp :refer [NewTTP StoredTTP realize-ttp]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-ttp (mc/create-handler-from-realized StoredTTP))
(def handle-read-ttp (mc/read-handler StoredTTP))
(def handle-update-ttp (mc/update-handler-from-realized StoredTTP))
(def handle-delete-ttp (mc/delete-handler StoredTTP))
