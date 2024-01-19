(ns ctia.schemas.graphql.resolvers
  (:require [clojure.tools.logging :as log]
            [ctia.domain.entities
             :refer
             [page-with-long-id with-long-id]]
            [ctia.graphql.delayed :as delayed]
            [ctia.schemas.core :as ctia-schemas
             :refer [GraphQLRuntimeContext
                     GraphQLValue
                     AnyGraphQLTypeResolver
                     RealizeFnResult]]
            [ctia.schemas.graphql.pagination :as pagination]
            [ctia.store :as store]
            [schema.core :as s]))

;; Default fields that must always be retrieved
(def default-fields [:id :type])

(defn- remove-map-empty-values
  [m]
  (into {} (filter second m)))

(s/defn ^:private search-entity :- pagination/Connection
  "Performs a query-string-search operation for a given entity type"
  [entity-type :- s/Keyword
   query :- s/Str
   filtermap :- {s/Keyword (s/maybe s/Str)}
   args :- {s/Keyword s/Any}
   ident
   field-selection
   with-long-id-fn
   {{{:keys [get-store]} :StoreService} :services} :- GraphQLRuntimeContext]
  (let [paging-params (pagination/connection-params->paging-params args)
        params (cond-> (select-keys paging-params [:limit :offset :sort_by])
                 field-selection (assoc :fields
                                        (concat default-fields
                                                field-selection)))]
    (log/debugf "Search entity %s graphql args %s" entity-type args)

    (some-> (get-store entity-type)
            (store/query-string-search
              {:search-query {:full-text  [{:query query}]
                              :filter-map (remove-map-empty-values filtermap)}
               :ident ident
               :params params})
            with-long-id-fn
            (pagination/result->connection-response paging-params))))

(s/defn search-entity-resolver :- AnyGraphQLTypeResolver
  [entity-type-kw]
  (s/fn :- (RealizeFnResult s/Any)
    [context args field-selection _src]
    (delayed/fn [{:keys [services]
                  :as rt-ctx} :- GraphQLRuntimeContext]
      (search-entity entity-type-kw
                     (:query args)
                     {}
                     args
                     (:ident context)
                     field-selection
                     #(page-with-long-id % services)
                     rt-ctx))))

(s/defn entity-by-id :- (RealizeFnResult GraphQLValue)
  [entity-type-kw :- s/Keyword
   id :- s/Str
   ident
   field-selection :- [s/Keyword]]
  (delayed/fn :- GraphQLValue
    [{{{:keys [get-store]} :StoreService
       :as services}
      :services} :- GraphQLRuntimeContext]
    (log/debugf "Retrieve %s (id:%s, fields:%s)"
                entity-type-kw
                id
                field-selection)
    (some-> (get-store entity-type-kw)
            (store/read-record id ident {:fields (concat default-fields field-selection)})
            (with-long-id services))))

(s/defn entity-by-id-resolver :- AnyGraphQLTypeResolver
  [entity-type-kw]
  (fn [context args field-selection _]
    (entity-by-id entity-type-kw
                  (:id args)
                  (:ident context)
                  field-selection)))

;;---- Feedback

(s/defn search-feedbacks-by-entity-id :- (RealizeFnResult GraphQLValue)
  [entity-id :- s/Str
   context :- {s/Keyword s/Any}
   args :- {s/Keyword s/Any}
   field-selection :- [s/Keyword]]
 (delayed/fn :- GraphQLValue
  [{{{:keys [get-store]} :StoreService} :services} :- GraphQLRuntimeContext]
  (let [paging-params (pagination/connection-params->paging-params args)
        params (cond-> (select-keys paging-params [:limit :offset :sort_by])
                 field-selection (assoc :fields
                                        (concat default-fields field-selection)))]
    (log/debug "Search feedback for entity id: " entity-id)
    (some-> (get-store :feedback)
            (store/list-records
              {:all-of {:entity_id entity-id}}
              (:ident context)
              params)
            (pagination/result->connection-response paging-params)))))

;;---- Judgement

(s/defn search-judgements-by-observable :- (RealizeFnResult pagination/Connection)
  [observable :- ctia-schemas/Observable
   context :- {s/Keyword s/Any}
   args :- {s/Keyword s/Any}
   field-selection :- [s/Keyword]]
 (delayed/fn :- pagination/Connection
  [{{{:keys [get-store]} :StoreService
     :as services}
    :services} :- GraphQLRuntimeContext]
  (let [paging-params (pagination/connection-params->paging-params args)
        params (cond-> (select-keys paging-params [:limit :offset :sort_by])
                 field-selection (assoc :fields
                                        (concat default-fields field-selection)))]
    (some-> (get-store :judgement)
            (store/list-judgements-by-observable
              observable
              (:ident context)
              params)
            (page-with-long-id services)
            (pagination/result->connection-response paging-params)))))

;;---- Sighting

(s/defn search-sightings-by-observable :- (RealizeFnResult pagination/Connection)
  [observable :- ctia-schemas/Observable
   context :- {s/Keyword s/Any}
   args :- {s/Keyword s/Any}
   field-selection :- [s/Keyword]]
 (delayed/fn :- pagination/Connection
  [{{{:keys [get-store]} :StoreService
     :as services}
    :services} :- GraphQLRuntimeContext]
  (let [paging-params (pagination/connection-params->paging-params args)
        params (cond-> (select-keys paging-params [:limit :offset :sort_by])
                 field-selection (assoc :fields
                                        (concat default-fields field-selection)))]
    (some-> (get-store :sighting)
            (store/list-sightings-by-observables
              [observable]
              (:ident context)
              params)
            (page-with-long-id services)
            (pagination/result->connection-response paging-params)))))

;;---- Relationship

(s/defn search-relationships :- (RealizeFnResult GraphQLValue)
  [context args field-selection src]
  (delayed/fn :- GraphQLValue
    [{:keys [services]
      :as rt-ctx} :- GraphQLRuntimeContext]
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
                     #(page-with-long-id % services)
                     rt-ctx))))

;;--- AssetMapping

(s/defn search-asset-mappings-by-asset-ref :- (RealizeFnResult GraphQLValue)
  [entity-id :- s/Str
   context :- {s/Keyword s/Any}
   args :- {s/Keyword s/Any}
   field-selection :- [s/Keyword]]
  (delayed/fn
    :- GraphQLValue
    [{{{:keys [get-store]} :StoreService} :services} :- GraphQLRuntimeContext]
    (let [paging-params (pagination/connection-params->paging-params args)
          params        (cond-> (select-keys paging-params [:limit :offset :sort_by])
                          field-selection (assoc :fields
                                                 (concat default-fields field-selection)))]
      (log/debug "Search for AssetMappings for asset-ref: " entity-id)
      (some-> (get-store :asset-mapping)
              (store/list-records
               {:all-of {:asset_ref entity-id}}
               (:ident context)
               params)
              (pagination/result->connection-response paging-params)))))

;;--- AssetProperties

(s/defn search-asset-properties-by-asset-ref :- (RealizeFnResult GraphQLValue)
  [entity-id :- s/Str
   context :- {s/Keyword s/Any}
   args :- {s/Keyword s/Any}
   field-selection :- [s/Keyword]]
  (delayed/fn
    :- GraphQLValue
    [{{{:keys [get-store]} :StoreService} :services} :- GraphQLRuntimeContext]
    (let [paging-params (pagination/connection-params->paging-params args)
          params        (cond-> (select-keys paging-params [:limit :offset :sort_by])
                          field-selection (assoc :fields
                                                 (concat default-fields field-selection)))]
      (log/debug "Search for AssetProperties for asset-ref: " entity-id)
      (some-> (get-store :asset-properties)
              (store/list-records
               {:all-of {:asset_ref entity-id}}
               (:ident context)
               params)
              (pagination/result->connection-response paging-params)))))
