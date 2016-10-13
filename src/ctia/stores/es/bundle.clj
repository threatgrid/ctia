(ns ctia.stores.es.bundle
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredBundle]]))

(def handle-create-bundle (crud/handle-create :bundle StoredBundle))
(def handle-read-bundle (crud/handle-read :bundle StoredBundle))
(def handle-delete-bundle (crud/handle-delete :bundle StoredBundle))
