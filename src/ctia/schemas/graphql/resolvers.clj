(ns ctia.schemas.graphql.resolvers
  (:require [clojure.tools.logging :as log]
            [ctia.domain.entities
             :refer
             [page-with-long-id un-store un-store-page with-long-id]]
            [ctia.schemas.core :as ctia-schemas]
            [ctia.schemas.graphql.pagination :as pagination]
            [ctia.store :refer :all]
            [schema.core :as s]))

;; Default fields that must always be retrieved
(def default-fields [:id :type])

(defn- remove-map-empty-values
  [m]
  (into {} (filter second m)))

(s/defn ^:private search-entity :- pagination/Connection
  "Performs a query-string-search-store operation for a given entity type"
  [entity-type :- s/Keyword
   query :- s/Str
   filtermap :- {s/Keyword (s/maybe s/Str)}
   args :- {s/Keyword s/Any}
   ident
   field-selection
   with-long-id-fn]
  (let [paging-params (pagination/connection-params->paging-params args)
        params (cond-> (select-keys paging-params [:limit :offset :sort_by])
                 field-selection (assoc :fields
                                        (concat default-fields
                                                field-selection)))]
    (log/debugf "Search entity %s graphql args %s" entity-type args)

    (some-> (query-string-search-store
             entity-type
             query-string-search
             query
             (remove-map-empty-values filtermap)
             ident
             params)
            with-long-id-fn
            un-store-page
            (pagination/result->connection-response paging-params))))

(defn search-entity-resolver
  [entity-type-kw]
  (fn [context args field-selection src]
    (search-entity entity-type-kw
                   (:query args)
                   {}
                   args
                   (:ident context)
                   field-selection
                   page-with-long-id)))

(s/defn entity-by-id
  [entity-type-kw :- s/Keyword
   id :- s/Str
   ident
   field-selection :- (s/maybe [s/Keyword])]
  (log/debugf "Retrieve %s (id:%s, fields:%s)"
              entity-type-kw
              id
              field-selection)
  (some-> (read-store entity-type-kw
                      read-fn
                      id
                      ident
                      {:fields (concat default-fields field-selection)})
          with-long-id
          un-store))

(defn entity-by-id-resolver
  [entity-type-kw]
  (fn [context args field-selection _]
    (entity-by-id entity-type-kw
                  (:id args)
                  (:ident context)
                  field-selection)))

;;---- Feedback

(s/defn search-feedbacks-by-entity-id
  [entity-id :- s/Str
   context :- {s/Keyword s/Any}
   args :- {s/Keyword s/Any}
   field-selection :- (s/maybe [s/Keyword])]
  (let [paging-params (pagination/connection-params->paging-params args)
        params (cond-> (select-keys paging-params [:limit :offset :sort_by])
                 field-selection (assoc :fields
                                        (concat default-fields field-selection)))]
    (log/debug "Search feedback for entity id: " entity-id)
    (some-> (read-store :feedback
                        list-records
                        {:entity_id entity-id}
                        {}
                        (:ident context)
                        params)
            un-store-page
            (pagination/result->connection-response paging-params))))

;;---- Judgement

(s/defn search-judgements-by-observable :- pagination/Connection
  [observable :- ctia-schemas/Observable
   context :- {s/Keyword s/Any}
   args :- {s/Keyword s/Any}
   field-selection :- (s/maybe [s/Keyword])]
  (let [paging-params (pagination/connection-params->paging-params args)
        params (cond-> (select-keys paging-params [:limit :offset :sort_by])
                 field-selection (assoc :fields
                                        (concat default-fields field-selection)))]
    (some-> (read-store :judgement
                        list-judgements-by-observable
                        observable
                        (:ident context)
                        params)
            page-with-long-id
            un-store
            (pagination/result->connection-response paging-params))))

;;---- Sighting

(s/defn search-sightings-by-observable :- pagination/Connection
  [observable :- ctia-schemas/Observable
   context :- {s/Keyword s/Any}
   args :- {s/Keyword s/Any}
   field-selection :- (s/maybe [s/Keyword])]
  (let [paging-params (pagination/connection-params->paging-params args)
        params (cond-> (select-keys paging-params [:limit :offset :sort_by])
                 field-selection (assoc :fields
                                        (concat default-fields field-selection)))]
    (some-> (read-store :sighting
                        list-sightings-by-observables
                        [observable]
                        (:ident context)
                        params)
            page-with-long-id
            un-store
            (pagination/result->connection-response paging-params))))

;;---- Relationship

(defn search-relationships
  [context args field-selection src]
  (let [{:keys [query relationship_type target_type]} args
        filtermap {:relationship_type relationship_type
                   :target_type target_type
                   :source_ref (:id src)}]
    (search-entity :relationship
                   query
                   filtermap
                   args
                   (:ident context)
                   (concat field-selection [:target_ref :source_ref])
                   page-with-long-id)))
