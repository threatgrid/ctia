(ns ctia.stores.atom.ttp
  (:require [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.schemas
             [indicator :refer [StoredIndicator]]
             [ttp :refer [StoredTTP]]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(def handle-create-ttp (mc/create-handler-from-realized StoredTTP))
(def handle-read-ttp (mc/read-handler StoredTTP))
(def handle-update-ttp (mc/update-handler-from-realized StoredTTP))
(def handle-delete-ttp (mc/delete-handler StoredTTP))
(def handle-list-ttps (mc/list-handler StoredTTP))

(s/defn handle-list-ttps-by-indicators :- (list-response-schema StoredTTP)
  [ttp-state :- (s/atom {s/Str StoredTTP})
   indicators :- [StoredIndicator]
   params]
  (let [ttp-ids (some->> (map :indicated_TTP indicators)
                         (mapcat #(map :ttp_id %))
                         set)]
    (handle-list-ttps ttp-state {:id ttp-ids} params)))
