(ns ctia.stores.es.actor
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core
             :refer [StoredActor PartialStoredActor]]))

(def handle-create (crud/handle-create :actor StoredActor))
(def handle-read (crud/handle-read :actor PartialStoredActor))
(def handle-update (crud/handle-update :actor StoredActor))
(def handle-delete (crud/handle-delete :actor StoredActor))
(def handle-list (crud/handle-find :actor PartialStoredActor))
(def handle-query-string-search (crud/handle-query-string-search :actor PartialStoredActor))
