(ns ctia.relationship.store.es
  (:require [ctia.schemas.core :refer [PartialStoredRelationship StoredRelationship]]
            [ctia.stores.es.store :refer [def-es-store]]))

(def-es-store RelationshipStore :relationship StoredRelationship PartialStoredRelationship)
