(ns ctia.stores.atom.actor
  (:require [ctia.schemas.actor :refer [NewActor StoredActor realize-actor]]
            [ctia.stores.atom.common :as mc]))

(def swap-actor (mc/make-swap-fn realize-actor))

(mc/def-create-handler handle-create-actor
  StoredActor NewActor swap-actor (mc/random-id "actor"))

(mc/def-read-handler handle-read-actor StoredActor)

(mc/def-delete-handler handle-delete-actor StoredActor)

(mc/def-update-handler handle-update-actor
  StoredActor NewActor swap-actor)
