(ns ctia.entity.incident.es-store
  (:require [ctia.entity.incident.schemas
             :refer [PartialStoredIncident StoredIncident]]
            [ctia.store :refer [IQueryStringSearchableStore IStore]]
            [ctia.stores.es.mapping :as em]
            [ctia.stores.es.store :refer [close-connections!]]
            [ctia.stores.es.crud :as crud]
            [clojure.string :as string]
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

(s/defn append-query-clauses :- SearchQuery
  [search-query  :- SearchQuery
   str-lucene-clauses :- [s/Str]]
  (let [query (get-in search-query [:full-text :query])
        prepared (string/join " OR " str-lucene-clauses)
        lucene-query (if query
                       ;; from previous observations the order of filters in lucene matters
                       ;; prepared is likely more optimized, add it first
                       (format "(%s) AND (%s)" prepared query)
                       prepared)]
    (assoc-in search-query [:full-text :query] lucene-query)))

(defn high-impact-sources
  [conn-state]
  (let [get-in-config (get-in conn-state [:services :ConfigService :get-in-config])]
    (some-> (get-in-config [:ctia :incident :high-impact :source])
            (string/split #","))))

(s/defn prepare-impact :- SearchQuery
  [conn-state
   search-query :-  SearchQuery]
  (let [sources (high-impact-sources conn-state)
        high_impact (get-in search-query [:filter-map :high_impact])
        query-clauses (map #(format "source:\"%s\"" %) sources)
        neg-query-clauses (map #(str "-" %) query-clauses)]
    (cond-> search-query
      (some? high_impact) (update :filter-map dissoc :high_impact)
      (false? high_impact) (append-query-clauses neg-query-clauses)
      (true? high_impact) (append-query-clauses query-clauses))))

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
