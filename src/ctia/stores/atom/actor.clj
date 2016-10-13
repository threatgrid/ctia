(ns ctia.stores.atom.actor
  (:require [ctia.schemas.core :refer [StoredActor]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-actor (mc/create-handler-from-realized StoredActor))
(def handle-read-actor (mc/read-handler StoredActor))
(def handle-list-actors (mc/list-handler StoredActor))
(def handle-update-actor (mc/update-handler-from-realized StoredActor))
(def handle-delete-actor (mc/delete-handler StoredActor))
