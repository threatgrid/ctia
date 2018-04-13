(ns ctia.actor.store.es
  (:require [ctia.schemas.core :refer [PartialStoredActor StoredActor]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store ActorStore :actor StoredActor PartialStoredActor)
