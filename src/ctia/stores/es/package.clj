(ns ctia.stores.es.package
  (:require [ctia.stores.es.crud :as crud]
            [ctim.schemas.package :refer [StoredPackage]]))

(def handle-create-package (crud/handle-create :bundle StoredPackage))
(def handle-read-package (crud/handle-read :bundle StoredPackage))
(def handle-delete-package (crud/handle-delete :bundle StoredPackage))
