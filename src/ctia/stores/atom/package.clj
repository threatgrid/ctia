(ns ctia.stores.atom.package
  (:require [ctim.schemas.package :refer [StoredPackage]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-package (mc/create-handler-from-realized StoredPackage))
(def handle-read-package (mc/read-handler StoredPackage))
(def handle-update-package (mc/update-handler-from-realized StoredPackage))
(def handle-delete-package (mc/delete-handler StoredPackage))
