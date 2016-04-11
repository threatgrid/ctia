(ns ctia.stores.atom.coa
  (:require [ctia.schemas.coa :refer [NewCOA StoredCOA]]
            [ctia.stores.atom.common :as mc]))

(mc/def-create-handler-from-realized handle-create-coa StoredCOA)

(mc/def-read-handler handle-read-coa StoredCOA)

(mc/def-delete-handler handle-delete-coa StoredCOA)

(mc/def-update-handler-from-realized handle-update-coa StoredCOA)
