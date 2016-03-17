(ns cia.stores.memory.ttp
  (:require [cia.schemas.ttp :refer [NewTTP StoredTTP realize-ttp]]
            [cia.store :refer [ITTPStore]]
            [cia.stores.memory.common :as mc]))

(def swap-ttp (mc/make-swap-fn realize-ttp))

(mc/def-create-handler handle-create-ttp
  StoredTTP NewTTP swap-ttp (mc/random-id "ttp"))

(mc/def-read-handler handle-read-ttp StoredTTP)

(mc/def-delete-handler handle-delete-ttp StoredTTP)

(mc/def-update-handler handle-update-ttp
  StoredTTP NewTTP swap-ttp)

(defrecord TTPStore [state]
  ITTPStore
  (read-ttp [_ id]
    (handle-read-ttp state id))
  (create-ttp [_ login new-ttp]
    (handle-create-ttp state login new-ttp))
  (update-ttp [_ id login new-ttp]
    (handle-update-ttp state id login new-ttp))
  (delete-ttp [_ id]
    (handle-delete-ttp state id))
  (list-ttps [_ filter-map]))
