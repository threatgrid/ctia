(ns ctia.stores.atom.bundle
  (:require [ctim.schemas.bundle :refer [StoredBundle]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-bundle (mc/create-handler-from-realized StoredBundle))
(def handle-read-bundle (mc/read-handler StoredBundle))
(def handle-update-bundle (mc/update-handler-from-realized StoredBundle))
(def handle-delete-bundle (mc/delete-handler StoredBundle))
