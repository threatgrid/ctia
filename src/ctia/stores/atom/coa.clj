(ns ctia.stores.atom.coa
  (:require [ctia.schemas.coa :refer [NewCOA StoredCOA realize-coa]]
            [ctia.stores.atom.common :as mc]))

(def swap-coa (mc/make-swap-fn realize-coa))

(mc/def-create-handler handle-create-coa
  StoredCOA NewCOA swap-coa (mc/random-id "coa"))

(mc/def-read-handler handle-read-coa StoredCOA)

(mc/def-delete-handler handle-delete-coa StoredCOA)

(mc/def-update-handler handle-update-coa
  StoredCOA NewCOA swap-coa)
