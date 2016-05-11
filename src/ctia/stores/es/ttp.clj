(ns ctia.stores.es.ttp
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.ttp :refer [StoredTTP]]))

(def handle-create-ttp (crud/handle-create :ttp StoredTTP))
(def handle-read-ttp (crud/handle-read :ttp StoredTTP))
(def handle-update-ttp (crud/handle-update :ttp StoredTTP))
(def handle-delete-ttp (crud/handle-delete :ttp StoredTTP))
(def handle-list-ttps (crud/handle-find :ttp StoredTTP))

(defn handle-list-ttps-by-indicators
  [state indicators params]
  (let [ttp-ids (some->> (map :indicated_TTP indicators)
                         (mapcat #(map :ttp_id %))
                         vec)]
    (handle-list-ttps state
                      {:id ttp-ids}
                      params)))
