(ns ctia.stores.memory.actor
  (:require [ctia.schemas.actor :refer [NewActor StoredActor realize-actor]]
            [ctia.store :refer [IActorStore]]
            [ctia.stores.memory.common :as mc]))

(def swap-actor (mc/make-swap-fn realize-actor))

(mc/def-create-handler handle-create-actor
  StoredActor NewActor swap-actor (mc/random-id "actor"))

(mc/def-read-handler handle-read-actor StoredActor)

(mc/def-delete-handler handle-delete-actor StoredActor)

(mc/def-update-handler handle-update-actor
  StoredActor NewActor swap-actor)

(defrecord ActorStore [state]
  IActorStore
  (read-actor [_ id]
    (handle-read-actor state id))
  (create-actor [_ login new-actor]
    (handle-create-actor state login new-actor))
  (update-actor [_ id login actor]
    (handle-update-actor state id login actor))
  (delete-actor [_ id]
    (handle-delete-actor state id))
  (list-actors [_ filter-map]))
