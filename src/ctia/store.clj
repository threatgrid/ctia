(ns ctia.store)

(defprotocol IStore
  (create-record [this new-records ident params])
  (read-record [this id ident params])
  (read-records [this ids ident params])
  (update-record [this id record ident params])
  (delete-record [this id ident params])
  (bulk-delete [this ids ident params])
  (bulk-update [this records ident params])
  (list-records [this filtermap ident params])
  (close [this]))

(defprotocol IJudgementStore
  (calculate-verdict [this observable ident]
                     [this observable ident params])
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
  (query-string-count [this search-query ident])
  (aggregate [this search-query agg-query ident])
  (delete-search [this search-query ident params]))

(def empty-stores
  {:actor []
   :asset []
   :asset-mapping []
   :asset-properties []
   :attack-pattern []
   :campaign []
   :casebook []
   :coa []
   :data-table []
   :event []
   :feed []
   :feedback []
   :identity []
   :identity-assertion []
   :incident []
   :indicator []
   :investigation []
   :judgement []
   :malware []
   :relationship []
   :sighting []
   :target-record []
   :tool []
   :vulnerability []
   :weakness []})
