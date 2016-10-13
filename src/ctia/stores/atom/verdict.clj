(ns ctia.stores.atom.verdict
  (:require [ctia.stores.atom.common :as mc]
            [ctia.schemas.core :refer [StoredVerdict]]))

(def handle-create (mc/create-handler-from-realized StoredVerdict))
(def handle-read (mc/read-handler StoredVerdict))
(def handle-delete (mc/delete-handler StoredVerdict))
(def handle-list (mc/list-handler StoredVerdict))
