(ns ctia.schemas.graphql.relationship
  (:require [ctim.domain.id :as id]
            [ctia.schemas.graphql.common :as c]
            [ctia.schemas.graphql.helpers :as g]
            [ctia.schemas.graphql.observable :as o]
            [ctia.schemas.graphql.pagination :as p]
            [ctia.schemas.graphql.refs :as refs]
            [ctia.schemas.graphql.resolvers
             :refer [entity-by-id
                     search-relationships]]
            [ctim.schemas.vocabularies :as voc]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [ctia.schemas.graphql.common :as common])
  (:import graphql.Scalars))

(def relationship-connection-type-name "RelationshipConnection")

(def RelationshipTypeType
  (g/enum "RelationshipType"
          ""
          voc/relationship-type))

(s/defn ref->entity-type :- s/Str
  "Extracts the entity type from the Reference"
  [ref :- s/Str]
  (some-> ref
          id/long-id->id
          :type))

(def Entity
  (g/new-union
   "Entity"
   ""
   (fn [obj args schema]
     (log/debug "Entity resolution" obj args)
     (case (:type obj)
       "judgement" (.getType schema refs/judgement-type-name)))
   [refs/JudgementRef]))

(def relation-fields
  (merge
   c/base-entity-fields
   c/describable-entity-fields
   c/sourcable-object-fields
   (g/non-nulls
    {:relationship_type {:type RelationshipTypeType}
     :source_ref {:type Scalars/GraphQLString
                  :description "The ID of the source object"}
     :source {:type Entity
              :resolve (fn [_ args src]
                         (log/debug "Source resolver" args src)
                         (let [ref (:source_ref src)
                               entity-type (ref->entity-type ref)]
                           (entity-by-id entity-type ref)))}
     :target {:type Entity
              :resolve (fn [_ args src]
                         (log/debug "Target resolver" args src)
                         (let [ref (:target_ref src)
                               entity-type (ref->entity-type ref)]
                           (entity-by-id entity-type ref)))}
     :target_ref {:type Scalars/GraphQLString
                  :description (str "The type of the relationship, "
                                    "see /ctia/doc/defined_relationships.md")}})))

(def relationship-type-name "Relationship")

(def RelationshipType
  (g/new-object relationship-type-name
                "Represents a relationship between two entities"
                []
                relation-fields))

(def RelationshipConnectionType
  (p/new-connection RelationshipType))

(def relatable-entity-fields
  {:relationships
   {:type RelationshipConnectionType
    :description (str "A connection containing the Relationship objects "
                      "that has this object a their source.")
    :args
    (merge
     common/lucene-query-arguments
     {:relationship_type
      {:type RelationshipTypeType
       :description (str "restrict to Relations with the specified relationship_type.")}
      :target_type
      {:type Scalars/GraphQLString
       :description (str "restrict to Relationships whose target is of the "
                         "specified CTIM entity type.")}}
     p/connection-arguments)
    :resolve search-relationships}})
