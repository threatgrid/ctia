(ns ctia.stores.atom.relationship
  (:require [ctia.schemas.core :refer [StoredRelationship]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-relationship (mc/create-handler-from-realized StoredRelationship))
(def handle-read-relationship (mc/read-handler StoredRelationship))
(def handle-list-relationships (mc/list-handler StoredRelationship))
(def handle-delete-relationship (mc/delete-handler StoredRelationship))
