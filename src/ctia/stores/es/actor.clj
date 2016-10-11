(ns ctia.stores.es.actor
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredActor]]))

(def handle-create-actor (crud/handle-create :actor StoredActor))
(def handle-read-actor (crud/handle-read :actor StoredActor))
(def handle-update-actor (crud/handle-update :actor StoredActor))
(def handle-delete-actor (crud/handle-delete :actor StoredActor))
(def handle-list-actors (crud/handle-find :actor StoredActor))
