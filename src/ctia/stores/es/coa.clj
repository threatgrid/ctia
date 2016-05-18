(ns ctia.stores.es.coa
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.coa :refer [StoredCOA]]))

(def handle-create-coa (crud/handle-create :coa StoredCOA))
(def handle-read-coa (crud/handle-read :coa StoredCOA))
(def handle-update-coa (crud/handle-update :coa StoredCOA))
(def handle-delete-coa (crud/handle-delete :coa StoredCOA))
(def handle-list-coas (crud/handle-find :coa StoredCOA))

(defn handle-list-coas-by-indicators
  [state indicators params]
  (let [coa-ids (some->> (map :related_COAs indicators)
                              (mapcat #(map :COA_id %))
                              vec)]
    (handle-list-coas state
                      {:id coa-ids}
                      params)))
