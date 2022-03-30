(ns ctia.store
  (:require [ctia.schemas.core :refer [APIHandlerServices]]
            [schema.core :as s]))

(defprotocol IStore
  (create-record [this new-records ident params])
  (read-record [this id ident params])
  (update-record [this id record ident params])
  (delete-record [this id ident params])
  (bulk-delete [this ids ident params])
  (bulk-update [this records ident params])
  (list-records [this filtermap ident params])
  (close [this]))

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

(def read-fn read-record)
(def create-fn create-record)
(def list-fn list-records)

(s/defn list-all-pages
  [entity
   list-fn
   filters
   identity-map
   params
   {{:keys [get-store]} :StoreService} :- APIHandlerServices]
  (loop [query-params params
         results []]
    (let [{:keys [data
                  paging]}
          (-> (get-store entity)
              (list-fn
                filters
                identity-map
                query-params))]
      (if-let [next-params (:next paging)]
        (recur (into query-params next-params)
               (into results data))
        (into results data)))))
