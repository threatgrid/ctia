(ns ctia.stores.atom.actor
  (:require [ctia.schemas.actor :refer [NewActor StoredActor realize-actor]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(def swap-actor (mc/make-swap-fn realize-actor))

(mc/def-create-handler-from-realized handle-create-actor StoredActor)

(comment "WORKING"
         (s/defn handle-create-actor :- StoredActor
           [state :- (s/atom {s/Str StoredActor})
            login :- s/Str
            actor :- StoredActor]
           (let [id (:id actor)]
             (get (swap! state assoc id actor) id))))

(mc/def-read-handler handle-read-actor StoredActor)

(mc/def-delete-handler handle-delete-actor StoredActor)

(mc/def-update-handler handle-update-actor
  StoredActor NewActor swap-actor)
