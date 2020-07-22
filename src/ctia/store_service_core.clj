(ns ctia.store-service-core)

(def empty-stores
  {:judgement []
   :indicator []
   :feed []
   :feedback []
   :campaign []
   :actor []
   :coa []
   :data-table []
   :sighting []
   :identity-assertion []
   :incident []
   :relationship []
   :identity []
   :attack-pattern []
   :malware []
   :tool []
   :event []
   :investigation []
   :casebook []
   :vulnerability []
   :weakness []})

(defn init [context]
  (assoc context
         :stores (atom empty-stores)))

(defn get-stores [{:keys [stores]}]
  stores)

(defn write-store [{:keys [stores]} store write-fn]
  (first (doall (map write-fn (store @stores)))))

(defn read-store [{:keys [stores]} store read-fn]
  (read-fn (first (get @stores store))))
