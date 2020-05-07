(ns ctia.store)

(defprotocol IStore
  (create-record [this new-records ident params])
  (read-record [this id ident params])
  (update-record [this id record ident params])
  (delete-record [this id ident params])
  (list-records [this filtermap ident params]))

(defprotocol IJudgementStore
  (calculate-verdict [this observable ident])
  (list-judgements-by-observable [this observable ident params]))

(defprotocol ISightingStore
  (list-sightings-by-observables [this observable ident params]))

(defprotocol IIdentityStore
  (read-identity [this login])
  (create-identity [this new-identity])
  (delete-identity [this org-id role]))

(defprotocol IEventStore
  (read-event [this id ident params])
  (create-events [this new-events])
  (list-events [this filtermap ident params]))

(defprotocol IQueryStringSearchableStore
  (query-string-search [this search-query ident params])
  (aggregate [this search-query agg-query ident]))

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

(defonce stores (atom empty-stores))

(defn write-store [store write-fn & args]
  (first (doall (map #(apply write-fn % args) (store @stores)))))

(defn read-store [store read-fn & args]
  (apply read-fn (first (get @stores store)) args))

(def read-fn read-record)
(def create-fn create-record)
(def list-fn list-records)

(defn list-all-pages
  [entity
   list-fn
   filters
   identity-map
   params]
  (loop [query-params params
         results []]
    (let [{:keys [data
                  paging]}
          (read-store entity
                      list-fn
                      filters
                      identity-map
                      query-params)]
      (if-let [next-params (:next paging)]
        (recur next-params (concat results data))
        (concat results data)))))
