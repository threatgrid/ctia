(ns ctia.schemas.graphql.resolvers
  (:require [clojure.tools.logging :as log]
            [ctia.domain.entities :as ctim-entities]
            [ctia.domain.entities
             [feedback :as ctim-feedback-entity]
             [indicator :as ctim-indicator-entity]
             [judgement :as ctim-judgement-entity]
             [relationship :as ctim-relationship-entity]
             [sighting :as ctim-sighting-entity]]
            [ctim.schemas
             [indicator :as ctim-indicator-schema]
             [judgement :as ctim-judgement-schema]
             [sighting :as ctim-sighting-schema]]
            [ctia.schemas.core :as ctia-schemas]
            [ctia.schemas.graphql.pagination :as pagination]
            [ctia.store :refer :all]
            [schema.core :as s]))

(defn- remove-map-empty-values
  [m]
  (into {} (filter second m)))

(s/defn ^:private search-entity :- pagination/Connection
  "Performs a query-string-search-store operation for a given entity type"
  [entity-type :- s/Keyword
   query :- s/Str
   filtermap :- {s/Keyword (s/maybe s/Str)}
   args :- {s/Keyword s/Any}
   page-with-long-id-fn]
  (let [paging-params (pagination/connection-params->paging-params args)
        params (select-keys paging-params [:limit :offset])]
    (log/debugf "Search entity %s graphql args %s" entity-type args)
    (some-> (query-string-search-store
             entity-type
             query-string-search
             query
             (remove-map-empty-values filtermap)
             params)
            page-with-long-id-fn
            ctim-entities/un-store-page
            (pagination/result->connection-response paging-params))))

;;---- Feedback

(s/defn search-feedbacks-by-entity-id
  [entity-id :- s/Str
   args :- {s/Keyword s/Any}]
  (let [paging-params (pagination/connection-params->paging-params args)
        params (select-keys paging-params [:limit :offset])]
    (log/debug "Search feedback for entity id: " entity-id)
    (some-> (read-store :feedback
                        list-feedback
                        {:entity_id entity-id}
                        params)
            ctim-feedback-entity/page-with-long-id
            ctim-entities/un-store-page
            (pagination/result->connection-response paging-params))))

;;---- Indicator

(defn search-indicators
  [_ args src]
  (search-entity :indicator
                 (:query args)
                 {}
                 args
                 ctim-indicator-entity/page-with-long-id))

(s/defn indicator-by-id
  [id :- s/Str]
  (some-> (read-store :indicator read-indicator id)
          ctim-indicator-entity/with-long-id
          ctim-entities/un-store))

;;---- Judgement

(defn search-judgements
  [_ args src]
  (search-entity :judgement
                 (:query args)
                 {}
                 args
                 ctim-judgement-entity/page-with-long-id))

(s/defn search-judgements-by-observable :- pagination/Connection
  [observable :- ctia-schemas/Observable
   args :- {s/Keyword s/Any}]
  (let [paging-params (pagination/connection-params->paging-params args)
        params (select-keys paging-params [:limit :offset])]
    (some-> (read-store :judgement
                        list-judgements-by-observable
                        observable
                        params)
            ctim-judgement-entity/page-with-long-id
            ctim-entities/un-store
            (pagination/result->connection-response paging-params))))

(s/defn judgement-by-id
  [id :- s/Str]
  (some-> (read-store :judgement read-judgement id)
          ctim-judgement-entity/with-long-id
          ctim-entities/un-store))

;;---- Sighting

(defn search-sightings
  [_ args src]
  (search-entity :sighting
                 (:query args)
                 {}
                 args
                 ctim-sighting-entity/page-with-long-id))

(s/defn sighting-by-id
  [id :- s/Str]
  (some-> (read-store :sighting read-sighting id)
          ctim-sighting-entity/with-long-id
          ctim-entities/un-store))

;;---- Relationship

(defn search-relationships
  [_ args src]
  (let [{:keys [query relationship_type target_type]} args
        filtermap {:relationship_type relationship_type
                   :target_type target_type
                   :source_ref (:id src)}]
    (search-entity :relationship
                   query
                   filtermap
                   args
                   ctim-relationship-entity/page-with-long-id)))

(s/defn entity-by-id :- s/Any
  [entity-type :- s/Str
   id :- s/Str]
  (condp = entity-type
    ctim-judgement-schema/type-identifier (judgement-by-id id)
    ctim-sighting-schema/type-identifier (sighting-by-id id)
    ctim-indicator-schema/type-identifier (indicator-by-id id)))
