(ns ctia.stores.memory.coa
  (:require [ctia.schemas.coa :refer [NewCOA StoredCOA realize-coa]]
            [ctia.store :refer [ICOAStore]]
            [ctia.stores.memory.common :as mc]))

(def swap-coa (mc/make-swap-fn realize-coa))

(mc/def-create-handler handle-create-coa
  StoredCOA NewCOA swap-coa (mc/random-id "coa"))

(mc/def-read-handler handle-read-coa StoredCOA)

(mc/def-delete-handler handle-delete-coa StoredCOA)

(mc/def-update-handler handle-update-coa
  StoredCOA NewCOA swap-coa)

(defrecord COAStore [state]
  ICOAStore
  (read-coa [_ id]
    (handle-read-coa state id))
  (create-coa [_ login new-coa]
    (handle-create-coa state login new-coa))
  (update-coa [_ id login new-coa]
    (handle-update-coa state id login new-coa))
  (delete-coa [_ id]
    (handle-delete-coa state id))
  (list-coas [_ filter-map]))
