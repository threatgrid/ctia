(ns ctia.schemas.graphql.resolvers
  (:require [clojure.tools.logging :as log]
            [ctia.domain.entities :as ent]
            [ctia.domain.entities.judgement :as j]
            [ctia.domain.entities.relationship :as rel]
            [ctia.domain.entities :as ent]
            [ctia.schemas.graphql.pagination :as p]
            [ctia.store :refer :all]
            [ctim.domain.id :as id]
            [schema.core :as s]))

(defn- remove-map-empty-values
  [m]
  (into {} (filter second m)))

(s/defn ^:private search-entity :- p/Connection
  [entity-type :- s/Keyword
   query :- s/Str
   filtermap :- {s/Keyword s/Str}
   args :- {s/Keyword s/Any}]
  (let [paging-params (p/connection-params->paging-params args)
        params (select-keys paging-params [:limit :offset])]
    (log/debugf "Search entity %s graphql args %s" entity-type args)
    (some-> (query-string-search-store
             entity-type
             query-string-search
             query
             (remove-map-empty-values filtermap)
             params)
            rel/page-with-long-id
            ent/un-store-page
            (p/result->connection-response paging-params))))

(defn search-relationships
  [_ args src]
  (let [{:keys [query relationship_type target_type]} args
        filtermap {:relationship_type relationship_type
                   :target_type target_type
                   :source_ref (:id src)}]
    (search-entity :relationship query filtermap args)))

(defn search-judgements
  [_ args src]
  (search-entity :judgement (:query args) {} args))

(s/defn judgement-by-id
  [id :- s/Str]
  (some-> (read-store :judgement read-judgement id)
          j/with-long-id
          ent/un-store))

(s/defn entity-by-id :- s/Any
  [entity-type :- s/Keyword
   id :- s/Str]
  (case entity-type
    "judgement" (judgement-by-id id)))
