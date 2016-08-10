(ns ctia.stores.atom.coa
  (:require [ctim.schemas.coa :refer [StoredCOA]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-coa (mc/create-handler-from-realized StoredCOA))
(def handle-read-coa (mc/read-handler StoredCOA))
(def handle-list-coas (mc/list-handler StoredCOA))
(def handle-update-coa (mc/update-handler-from-realized StoredCOA))
(def handle-delete-coa (mc/delete-handler StoredCOA))
