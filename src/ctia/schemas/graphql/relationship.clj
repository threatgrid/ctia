(ns ctia.schemas.graphql.relationship
  (:require [ctia.schemas.graphql.common :as c]
            [ctia.schemas.graphql.helpers :as g]
            [ctia.schemas.graphql.observable :as o]
            [ctia.schemas.graphql.pagination :as p]
            [ctia.store :refer :all]
            [ctim.schemas.vocabularies :as voc]
            [clojure.tools.logging :as log]
            [flanders.example :refer :all])
  (:import graphql.Scalars))

(def relationship-connection-type-name "RelationshipConnection")

(def RelationshipTypeType
  (g/enum "RelationshipType"
          ""
          voc/relationship-type))

(def relation-fields
  (merge
   c/base-entity-fields
   c/describable-entity-fields
   c/sourcable-object-fields
   (g/non-nulls
    {:relationship_type {:type RelationshipTypeType}
     :source_ref {:type Scalars/GraphQLString
                  :description "The ID of the source object"}
     :target_ref {:type Scalars/GraphQLString
                  :description (str "The type of the relationship, "
                                    "see /ctia/doc/defined_relationships.md")}})))

(def relationship-type-name "Relationship")

(def RelationshipType
  (g/new-object relationship-type-name
                "Represents a relationship between two entities"
                []
                relation-fields))

(def RelationshipsConnectionType
  (p/new-connection RelationshipType
                    "relationships"))

(defn- remove-empty-values
  [m]
  (into {} (filter second m)))

(defn search-relationships
  [_ args src]
  (let [{:keys [query relationship_type target_type
                after first before last]} args
        filter-map {:relationship_type relationship_type
                    :target_type target_type
                    :source_ref (:id src)}
        params {}
        result (query-string-search-store
                :relationship
                query-string-search
                query
                (remove-empty-values filter-map)
                params)]
    (log/debug "Search relationships graphql args " args)
    (log/debug "Search relationhips filter " filter-map)
    (log/debug "Search relationhips filter " params)
    {:pageInfo {:hasNextPage true
                :hasPreviousPage false
                :startCursor 1
                :endCursor 1}
     :totalCount 0
     :edges []}))

(def relatable-entity-fields
  {:relationships
   {:type RelationshipsConnectionType
    :description (str "A connection containing the Relationship objects "
                      "that has this object a their source.")
    :args
    (merge
     {:query
      {:type Scalars/GraphQLString
       :description (str "A Lucense query string, will only "
                         "return Relationships matching it.")}
      :relationship_type
      {:type RelationshipTypeType
       :description (str "restrict to Relations with the specified relationship_type.")}
      :target_type
      {:type Scalars/GraphQLString
       :description (str "restrict to Relationships whose target is of the "
                         "specified CTIM entity type.")}}
     p/connection-arguments)
    :resolve search-relationships}})
