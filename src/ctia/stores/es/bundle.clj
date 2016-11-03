(ns ctia.stores.es.bundle
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredBundle]]))

(def handle-create (crud/handle-create :bundle StoredBundle))
(def handle-read (crud/handle-read :bundle StoredBundle))
(def handle-delete (crud/handle-delete :bundle StoredBundle))
