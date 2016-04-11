(ns ctia.stores.atom.actor
  (:require [ctia.schemas.actor :refer [NewActor StoredActor realize-actor]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(def handle-create-actor (mc/create-handler-from-realized StoredActor))

(mc/def-read-handler handle-read-actor StoredActor)

(mc/def-delete-handler handle-delete-actor StoredActor)

(def handle-update-actor (mc/update-handler-from-realized StoredActor))
