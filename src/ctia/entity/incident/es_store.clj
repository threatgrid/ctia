(ns ctia.entity.incident.es-store
  (:require [ctia.entity.incident.schemas
             :refer [PartialStoredIncident StoredIncident]]
            [ctia.store :refer [IQueryStringSearchableStore IStore]]
            [ctia.stores.es.mapping :as em]
            [ctia.stores.es.store :refer [close-connections!]]
            [ctia.stores.es.crud :as crud]
            [schema.core :as s]
            [ctia.schemas.search-agg :refer [SearchQuery]]))

(def incident-mapping
  {"incident"
   {:dynamic false
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:confidence em/token
      :status em/token
      :incident_time em/incident-time
      :categories em/token
      :discovery_method em/token
      :intended_effect em/token
      :assignees em/token
      :promotion_method em/token})}})

(def handle-create (crud/handle-create :incident StoredIncident))
(def handle-update (crud/handle-update :incident StoredIncident))
(def handle-read (crud/handle-read PartialStoredIncident))
(def handle-delete (crud/handle-delete :incident))
(def handle-list (crud/handle-find PartialStoredIncident))
(def handle-bulk-delete crud/bulk-delete)
(def handle-query-string-search (crud/handle-query-string-search PartialStoredIncident))
(def handle-query-string-count crud/handle-query-string-count)
(def handle-aggregate crud/handle-aggregate)
(def handle-delete-search crud/handle-delete-search)

(s/defn append-query-clause :- SearchQuery
  [search-query  :- SearchQuery
   str-lucene-clause :- s/Str]
  (let [query (get-in search-query [:full-text :query])
        lucene-query (cond->> str-lucene-clause
                       query (str query " "))]
    (assoc-in search-query [:full-text :query] lucene-query)))

(s/defn prepare-impact :- SearchQuery
  [conn-state
   search-query :-  SearchQuery]
  (let [get-in-config (get-in conn-state [:services :ConfigService :get-in-config])
        high_impact_source (get-in-config [:ctia :incident :high-impact :source])
        high_impact (get-in search-query [:filter-map :high_impact])]
    (clojure.pprint/pprint get-in-config)
    (cond-> search-query
      (some? high_impact) (update :filter-map dissoc :high_impact)
      (false? high_impact) (append-query-clause (format "-source:\"%s\"" high_impact_source))
      (true? high_impact) (assoc-in [:filter-map :source] high_impact_source))))

(defrecord IncidentStore [state]
  IStore
  (create-record [_ new-incidents ident params]
    (handle-create state new-incidents ident params))
  (read-record [_ id ident params]
    (handle-read state id ident params))
  (delete-record [_ id ident params]
    (handle-delete state id ident params))
  (update-record [_ id incident ident params]
    (handle-update state id incident ident params))
  (bulk-delete [_ ids ident params]
    (handle-bulk-delete state ids ident params))
  (list-records [_ filter-map ident params]
    (handle-list state filter-map ident params))
  (close [_] (close-connections! state))

  IQueryStringSearchableStore
  (query-string-search [_ search-query ident params]
    (handle-query-string-search state
                                (prepare-impact state search-query)
                                ident
                                params))
  (query-string-count [_ search-query ident]
    (handle-query-string-count state
                               (prepare-impact state search-query)
                               ident))
  (aggregate [_ search-query agg-query ident]
    (handle-aggregate state
                      (prepare-impact state search-query)
                      agg-query
                      ident))
  (delete-search [_ search-query ident params]
    (handle-delete-search state
                          (prepare-impact state search-query)
                          ident
                          params)))
