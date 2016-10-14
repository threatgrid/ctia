(ns ctia.stores.es.relationship
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredRelationship]]))

(def handle-create-relationship (crud/handle-create :relationship StoredRelationship))
(def handle-read-relationship (crud/handle-read :relationship StoredRelationship))
(def handle-delete-relationship (crud/handle-delete :relationship StoredRelationship))
(def handle-list-relationships (crud/handle-find :relationship StoredRelationship))
