(ns ctia.stores.atom.coa
  (:require [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.schemas
             [coa :refer [StoredCOA]]
             [indicator :refer [StoredIndicator]]]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(def handle-create-coa (mc/create-handler-from-realized StoredCOA))
(def handle-read-coa (mc/read-handler StoredCOA))
(def handle-update-coa (mc/update-handler-from-realized StoredCOA))
(def handle-delete-coa (mc/delete-handler StoredCOA))
(def handle-list-coas (mc/list-handler StoredCOA))

(s/defn handle-list-coas-by-indicators :- (list-response-schema StoredCOA)
  [coa-state :- (s/atom {s/Str StoredCOA})
   indicators :- [StoredIndicator]
   params]
  (let [coa-ids (some->> (map :related_COAs indicators)
                              (mapcat #(map :COA_id %))
                              set)]
    (handle-list-coas coa-state {:id coa-ids} params)))
