(ns ctia.store
  (:require [ctia.store-service :as store-svc]))

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
  (query-string-count [this search-query ident])
  (aggregate [this search-query agg-query ident]))

(defn get-global-stores []
  {:post [%]}
  (store-svc/get-stores @store-svc/global-store-service))

(defn deref-global-stores []
  {:post [%]}
  @(get-global-stores))

(defn write-store [store write-fn & args]
  (store-svc/write-store @store-svc/global-store-service
                         store
                         #(apply write-fn % args)))

(defn read-store [store read-fn & args]
  (store-svc/read-store @store-svc/global-store-service
                        store
                        #(apply read-fn % args)))

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
        (recur next-params (into results data))
        (into results data)))))
