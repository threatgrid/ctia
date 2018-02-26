(ns ctia.stores.es.relationship
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core
             :refer [StoredRelationship PartialStoredRelationship]]))

(def handle-create (crud/handle-create :relationship StoredRelationship))
(def handle-read (crud/handle-read :relationship PartialStoredRelationship))
(def handle-update (crud/handle-update :relationship StoredRelationship))
(def handle-delete (crud/handle-delete :relationship StoredRelationship))
(def handle-list (crud/handle-find :relationship PartialStoredRelationship))
(def handle-query-string-search (crud/handle-query-string-search :relationship PartialStoredRelationship))
