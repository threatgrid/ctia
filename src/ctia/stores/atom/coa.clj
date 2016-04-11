(ns ctia.stores.atom.coa
  (:require [ctia.schemas.coa :refer [NewCOA StoredCOA]]
            [ctia.stores.atom.common :as mc]))

(def handle-create-coa (mc/create-handler-from-realized StoredCOA))

(mc/def-read-handler handle-read-coa StoredCOA)

(mc/def-delete-handler handle-delete-coa StoredCOA)

(def handle-update-coa (mc/update-handler-from-realized StoredCOA))
